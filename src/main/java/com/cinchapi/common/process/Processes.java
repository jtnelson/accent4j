/*
 * Copyright (c) 2013-2016 Cinchapi Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cinchapi.common.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cinchapi.common.base.Platform;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Utility functions for safely handling {@link Process} objects.
 * 
 * @author Jeff Nelson
 */
public class Processes {

    /**
     * Create a {@link ProcessBuilder} that, on the appropriate platforms,
     * sources the standard interactive profile for the user (i.e.
     * ~/.bash_profile).
     * 
     * @param commands a string array containing the program and its arguments
     * @return a {@link ProcessBuilder}
     */
    public static ProcessBuilder getBuilder(String... commands) {
        ProcessBuilder pb = new ProcessBuilder(commands);
        if(!Platform.isWindows()) {
            Map<String, String> env = pb.environment();
            env.put("BASH_ENV",
                    System.getProperty("user.home") + "/.bash_profile");
        }
        return pb;
    }

    /**
     * Create a {@link ProcessBuilder} that, on the appropriate platforms,
     * sources the standard interactive profile for the user (i.e.
     * ~/.bash_profile) and supports the use of the pipe (|) redirection on
     * platforms that allow it.
     * 
     * @param commands a string array containing the program and its arguments
     * @return a {@link ProcessBuilder}
     */
    public static ProcessBuilder getBuilderWithPipeSupport(String... commands) {
        if(!Platform.isWindows()) {
            List<String> listCommands = Lists
                    .newArrayListWithCapacity(commands.length + 2);
            // Need to invoke a shell in which the commands can be run. That
            // shell will properly interpret the pipe(|).
            listCommands.add("/bin/sh");
            listCommands.add("-c");
            for (String command : commands) {
                listCommands.add(command);
            }
            return getBuilder(listCommands.toArray(commands));
        }
        else {
            return getBuilder(commands);
        }
    }

    /**
     * Return the pid of the current process.
     * 
     * @return pid.
     */
    public static String getCurrentPid() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    /**
     * Get the stderr for {@code process}.
     * 
     * @param process
     * @return a collection of error lines
     */
    public static List<String> getStdErr(Process process) {
        return readStream(process.getErrorStream());
    }

    /**
     * Get the stdout for {@code process}.
     * 
     * @param process
     * @return a collection of output lines
     */
    public static List<String> getStdOut(Process process) {
        return readStream(process.getInputStream());

    }

    /**
     * Check if the process with the processId is running.
     * 
     * @param pid Id for the input process.
     * @return true if its running, false if not.
     */
    public static boolean isPidRunning(String pid) {
        Process process = null;
        try {
            if(Platform.isLinux() || Platform.isMacOsX()
                    || Platform.isSolaris()) {
                ProcessBuilder pb = getBuilderWithPipeSupport(
                        "ps aux | grep <pid>");
                process = pb.start();
            }
            else if(Platform.isWindows()) {
                process = Runtime.getRuntime().exec(
                        "TASKLIST /fi \"PID eq " + pid + "\" /fo csv /nh");
            }
            else {
                throw new UnsupportedOperationException(
                        "Cannot check pid on the underlying platform");
            }
        }
        catch (IOException e) {
            Throwables.propagate(e);
        }
        if(process != null) {
            ProcessResult result = waitForSuccessfulCompletion(process);
            for (String line : result.out()) {
                if(line.contains(pid)) {
                    return true;
                }
            }
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Execute {@link Process#waitFor()} while reading everything from the
     * {@code process}'s standard out and error to prevent the process from
     * hanging.
     * 
     * @param process the {@link Process} for which to wait
     * @return a map containing all the {@link ProcessData} (e.g. exit code,
     *         stdout and stderr)
     */
    public static ProcessResult waitFor(Process process) {
        AtomicBoolean finished = new AtomicBoolean(false);
        List<String> stdout = Lists.newArrayList();
        List<String> stderr = Lists.newArrayList();
        CountDownLatch latch = new CountDownLatch(2);
        try {
            // Asynchronously exhaust stdout so process doesn't hang
            executor.execute(() -> {
                try {
                    InputStreamReader reader = new InputStreamReader(
                            process.getInputStream());
                    while (!finished.get()) {
                        stdout.addAll(CharStreams.readLines(reader));
                    }
                    reader.close();
                    latch.countDown();
                }
                catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            });

            // Asynchronously exhaust stderr so process doesn't hang
            executor.execute(() -> {
                try {
                    InputStreamReader reader = new InputStreamReader(
                            process.getErrorStream());
                    while (!finished.get()) {
                        stderr.addAll(CharStreams.readLines(reader));
                    }
                    reader.close();
                    latch.countDown();
                }
                catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            });
            int code = process.waitFor();
            finished.set(true);
            latch.await();
            return new ProcessResult(code, stdout, stderr);
        }
        catch (InterruptedException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Similar to {@link Process#waitFor()} but will throw a
     * {@link RuntimeException} if the process does not have an exit code of
     * {@code 0}.
     * 
     * @param process
     * @return the {@link ProcessResult}
     */
    public static ProcessResult waitForSuccessfulCompletion(Process process) {
        ProcessResult result = waitFor(process);
        if(result.exitCode() != 0) {
            List<String> msg = result.out().isEmpty() ? result.err() : result.out();
            throw new RuntimeException(msg.toString());
        }
        return result;
    }

    /**
     * Read an input stream.
     * 
     * @param stream
     * @return the lines in the stream
     */
    private static List<String> readStream(InputStream stream) {
        try (BufferedReader out = new BufferedReader(
                new InputStreamReader(stream))) {
            String line;
            List<String> output = Lists.newArrayList();
            while ((line = out.readLine()) != null) {
                output.add(line);
            }
            return output;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * An {@link Executor} that is used to asynchronously read input from a
     * processe's standard out and error streams.
     */
    private static final ExecutorService executor = MoreExecutors
            .getExitingExecutorService(
                    (ThreadPoolExecutor) Executors.newFixedThreadPool(
                            Runtime.getRuntime().availableProcessors() * 2));

    private Processes() {} /* no-op */

    /**
     * The result of a {@link Process} that has been
     * {@link Processes#waitFor(Process) waited on}.
     * 
     * @author Jeff Nelson
     */
    public static final class ProcessResult {

        private final int exitCode;
        private final List<String> stderr;
        private final List<String> stdout;

        /**
         * Construct a new instance.
         * 
         * @param exitCode
         * @param stdout
         * @param stderr
         */
        private ProcessResult(int exitCode, List<String> stdout,
                List<String> stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        /**
         * Return the {@link Process process's} standard error.
         * 
         * @return stderr
         */
        public List<String> err() {
            return Collections.unmodifiableList(stderr);
        }

        /**
         * Return the {@link Process process's} exit code.
         * 
         * @return the exit code
         */
        public int exitCode() {
            return exitCode;
        }

        /**
         * Return the {@link Process process's} standard output.
         * 
         * @return stdout
         */
        public List<String> out() {
            return Collections.unmodifiableList(stdout);
        }
    }
}
