/*
 * Copyright (c) 2013-2018 Cinchapi Inc.
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
package com.cinchapi.common.collect;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Unit tests for {@link AnyMaps}.
 *
 * @author Jeff Nelson
 */
public class AnyMapsTest {
 
    @Test
    public void testRename() {
        Map<String, Object> map = Maps.newHashMap();
        Object value = 17;
        map.put("foo", value);
        AnyMaps.rename("foo", "bar", map);
        Assert.assertFalse(map.containsKey("foo"));
        Assert.assertNull(map.get("foo"));
        Assert.assertTrue(map.containsKey("bar"));
        Assert.assertEquals(value, map.get("bar"));
    }
    
    @Test
    public void testNavigateWhenKeyPresent() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("foo", ImmutableMap.of("bar", ImmutableMap.of("baz", 1)));
        map.put("foo.bar.baz.car.bang", 2);
        Assert.assertEquals(1, (int) AnyMaps.navigate("foo.bar.baz", map));
        Assert.assertEquals(2, (int) AnyMaps.navigate("foo.bar.baz.car.bang", map));
        Assert.assertNull(AnyMaps.navigate("foo.bar.baz.car", map));
        Assert.assertNull(AnyMaps.navigate("foo.bar.baz.1", map));
        Assert.assertEquals(ImmutableMap.of("baz", 1), AnyMaps.navigate("foo.bar", map));
    }
}
