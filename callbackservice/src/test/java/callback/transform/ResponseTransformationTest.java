/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package callback.transform;

import callback.BaseCallbackTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author phaneesh
 */
public class ResponseTransformationTest extends BaseCallbackTest {

    @Test
    public void testSimpleArraySerialization() throws Exception {
        JsonNode node = mapper.readTree("[]");
        assertTrue(node.isArray());
    }

    @Test
    public void testSimpleObjectSerialization() throws Exception {
        JsonNode node = mapper.readTree("{}");
        assertTrue(node.isObject());
    }

}
