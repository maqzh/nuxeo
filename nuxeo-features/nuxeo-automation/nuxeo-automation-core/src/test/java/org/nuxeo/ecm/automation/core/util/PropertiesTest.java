/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Ronan DANIELLOU <rdaniellou@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;

import org.assertj.core.data.MapEntry;
import org.junit.Test;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.junit.runner.RunWith;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class PropertiesTest {

    @Test
    public void loadProperties() throws IOException {

        final String newLine = "\n";
        final String newLineInterpretedInInputData = "\\\n";
        final String key1 = "schema:property";
        final String key2 = "key2";
        final String comment = "#This is a comment";

        String[] inputValues = { "line 1", " line 2", "", "line 3 ", "  line 5 ", "", "",
                "#this is not a comment because it follows a multi-line value" };

        // loops on input values, building a multi-line single value input

        String valueExpected = "";
        String valueIn = "";

        for (int lineNumber = 0; lineNumber < inputValues.length; lineNumber++) {
            if (lineNumber == 0) {
                valueIn = inputValues[lineNumber];
                valueExpected = valueIn;
            } else {
                valueIn += newLineInterpretedInInputData + inputValues[lineNumber];
                valueExpected += newLine + inputValues[lineNumber];
            }
            StringReader strReader = new StringReader(key1 + "=" + valueIn);
            assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));
        }

        // a comment at the end is ignored

        StringReader strReader = new StringReader(key1 + "=" + valueIn + newLine + comment);
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));

        // empty lines at the end are ignored

        strReader = new StringReader(key1 + "=" + valueIn + newLine);
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));

        strReader = new StringReader(key1 + "=" + valueIn + newLine + newLine);
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));

        // 2 values separated by a comment

        strReader = new StringReader(key1 + "=" + valueIn + newLine + comment + newLine + key2 + "=" + valueIn);
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected),
                MapEntry.entry(key2, valueExpected));

        // comment and empty lines at the beginning are ignored

        strReader = new StringReader(comment + newLine + newLine + key1 + "=" + "line 1");
        valueExpected = "line 1";
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));

        // empty line at the beginning is ignored

        strReader = new StringReader(newLine + key1 + "=" + "line 1");
        valueExpected = "line 1";
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));

        // empty value is accepted

        strReader = new StringReader(key1 + "=");
        valueExpected = "";
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key1, valueExpected));

        // keys are trimmed, values are not

        valueExpected = "  myValue";
        String key = "keyTrimmed";
        strReader = new StringReader("  " + key + " =" + valueExpected);
        assertThat(Properties.loadProperties(strReader)).containsOnly(MapEntry.entry(key, valueExpected));

    }
}
