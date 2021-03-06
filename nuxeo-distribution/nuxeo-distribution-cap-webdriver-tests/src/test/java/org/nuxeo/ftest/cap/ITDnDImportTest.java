/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.ftest.cap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

import org.nuxeo.functionaltests.AbstractTest;
import org.nuxeo.functionaltests.pages.DocumentBasePage;
import org.nuxeo.functionaltests.pages.DocumentBasePage.UserNotConnectedException;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Test Drag and Drop Import feature.
 *
 * @since 5.9.1
 */
public class ITDnDImportTest extends AbstractTest {

    private final static String WORKSPACE_TITLE = "WorkspaceTitle_" + new Date().getTime();

    private final static String CVDZ_ID = "CVDZ";

    private final static String DROP_ZONE_TARGET_CSS_CLASS = "dropzoneTarget";

    private final static String DROP_ZONE_CSS_CLASS = "dropzone";

    @FindBy(xpath = "//div[@id='CVDZ']")
    public WebElement dropZone;

    private JavascriptExecutor js;

    private void dragoverMockFileFF() {
        js.executeScript(String.format("jQuery('#%s').dropzone();", CVDZ_ID));
        js.executeScript(String.format(
                "var dragoverEvent = window.document.createEvent('DragEvents');"
                        + "dragoverEvent.initDragEvent('dragover', true, true, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null, null);"
                        + "var target = document.getElementById('%s');" + "target.dispatchEvent(dragoverEvent);",
                CVDZ_ID));
        js.executeScript(String.format(
                "var dragenterEvent = window.document.createEvent('DragEvents');"
                        + "dragenterEvent.initDragEvent('dragenter', true, true, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null, null);"
                        + "var target = document.getElementById('%s');" + "target.dispatchEvent(dragenterEvent);",
                CVDZ_ID));

    }

    /**
     * Test that the drop zone is reactive.
     *
     * @throws UserNotConnectedException
     * @since 5.9.1
     */
    @Test
    public void testDropZone() throws UserNotConnectedException {
        js = driver;

        DocumentBasePage documentBasePage = login();

        documentBasePage = documentBasePage.getNavigationSubPage().goToDocument("Workspaces");

        createWorkspace(documentBasePage, WORKSPACE_TITLE, null);

        WebElement dropzone = driver.findElement(By.id(CVDZ_ID));

        // Check that
        String cssClass = dropzone.getAttribute("class");
        assertNotNull(cssClass);
        assertTrue(cssClass.contains(DROP_ZONE_CSS_CLASS));
        assertFalse(cssClass.contains(DROP_ZONE_TARGET_CSS_CLASS));

        dragoverMockFileFF();

        // Check that the dropzone has detected the the dragover
        cssClass = dropzone.getAttribute("class");
        assertNotNull(cssClass);
        assertTrue(cssClass.contains(DROP_ZONE_TARGET_CSS_CLASS));

        deleteWorkspace(documentBasePage, WORKSPACE_TITLE);

        logout();
    }

}
