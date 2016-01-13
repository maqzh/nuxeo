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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 */
package org.nuxeo.ftest.cap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.nuxeo.functionaltests.AbstractTest;
import org.nuxeo.functionaltests.AjaxRequestManager;
import org.nuxeo.functionaltests.Locator;
import org.nuxeo.functionaltests.forms.Select2WidgetElement;
import org.nuxeo.functionaltests.pages.DocumentBasePage;
import org.nuxeo.functionaltests.pages.DocumentBasePage.UserNotConnectedException;
import org.nuxeo.functionaltests.pages.FileDocumentBasePage;
import org.nuxeo.functionaltests.pages.admincenter.usermanagement.UsersGroupsBasePage;
import org.nuxeo.functionaltests.pages.admincenter.usermanagement.UsersTabSubPage;
import org.nuxeo.functionaltests.pages.tabs.EditTabSubPage;
import org.nuxeo.functionaltests.pages.tabs.PermissionsSubPage;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import com.google.common.base.Function;

/**
 * Safe Edit feature tests.
 *
 * @since 5.7.1
 */
public class ITSafeEditTest extends AbstractTest {

    public final static String COVERAGE = "France";

    /**
     * Convenient class to access localstorage of the browser.
     *
     * @since 5.7.1
     */
    public class LocalStorage {
        private final JavascriptExecutor js;

        public LocalStorage(WebDriver webDriver) {
            js = (JavascriptExecutor) webDriver;
        }

        public void clearLocalStorage() {
            js.executeScript(String.format("window.localStorage.clear();"));
        }

        public String getItemFromLocalStorage(String key) {
            return (String) js.executeScript(String.format("return window.localStorage.getItem('%s');", key));
        }

        public String getKeyFromLocalStorage(int key) {
            return (String) js.executeScript(
                    String.format("return window.localStorage.key('%s');", Integer.valueOf(key)));
        }

        public Long getLocalStorageLength() {
            return (Long) js.executeScript("return window.localStorage.length;");
        }

        public boolean isItemPresentInLocalStorage(String item) {
            return !(js.executeScript(String.format("return window.localStorage.getItem('%s');", item)) == null);
        }

        public void removeItemFromLocalStorage(String item) {
            js.executeScript(String.format("window.localStorage.removeItem('%s');", item));
        }

        public void setItemInLocalStorage(String item, String value) {
            js.executeScript(String.format("window.localStorage.setItem('%s','%s');", item, value));
        }

    }

    private static final Log log = LogFactory.getLog(AbstractTest.class);

    private final static String WORKSPACE_TITLE = "WorkspaceTitle_" + new Date().getTime();

    private final static String NEW_WORKSPACE_TITLE = "newWorkspaceName";

    private final static String DESCRIPTION_ELT_ID = "document_edit:nxl_heading:nxw_description";

    private final static String TITLE_ELT_ID = "document_edit:nxl_heading:nxw_title";

    private final static String INITIAL_DESCRIPTION = "workspaceDescription";

    private final static String DRAFT_SAVE_TEXT_NOTIFICATION = "Draft saved";

    private void prepare() throws Exception {
        DocumentBasePage documentBasePage;
        DocumentBasePage s = login();

        // Create a new user if not exist
        UsersGroupsBasePage page;
        UsersTabSubPage usersTab = s.getAdminCenter().getUsersGroupsHomePage().getUsersTab();
        usersTab = usersTab.searchUser(TEST_USERNAME);
        if (!usersTab.isUserFound(TEST_USERNAME)) {
            page = usersTab.getUserCreatePage().createUser(TEST_USERNAME, TEST_USERNAME, "lastname1", "company1",
                    "email1", TEST_PASSWORD, "members");
            usersTab = page.getUsersTab(true);
        } // search user usersTab =
        usersTab.searchUser(TEST_USERNAME);
        assertTrue(usersTab.isUserFound(TEST_USERNAME));

        // create a new wokspace and grant all rights to the test user
        documentBasePage = usersTab.exitAdminCenter()
                                   .getHeaderLinks()
                                   .getNavigationSubPage()
                                   .goToDocument("Workspaces");
        DocumentBasePage workspacePage = createWorkspace(documentBasePage, WORKSPACE_TITLE, INITIAL_DESCRIPTION);
        PermissionsSubPage permissionsSubPage = workspacePage.getPermissionsTab();
        // Need WriteSecurity (so in practice Manage everything) to edit a
        // Workspace
        if (!permissionsSubPage.hasPermissionForUser("Manage everything", TEST_USERNAME)) {
            permissionsSubPage.grantPermissionForUser("Manage everything", TEST_USERNAME);
        }
        logout();
    }

    private void byPassPopup(String message, boolean accept) {
        Alert alert = driver.switchTo().alert();
        assertEquals(message, alert.getText());
        if (accept) {
            alert.accept();
        } else {
            alert.dismiss();
        }
    }

    private void byPassLeavePagePopup(boolean accept) {
        byPassPopup(
                "This page is asking you to confirm that you want to leave - data you have entered may not be saved.",
                accept);
    }

    private void byPassLeaveTabPopup(boolean accept) {
        byPassPopup("This draft contains unsaved changes.", accept);
    }

    private void checkSafeEditRestoreProvided() {
        // We must find the status message asking if we want to restore
        // previous unchanged data, and make sure it is visible
        Wait<WebDriver> wait = new FluentWait<WebDriver>(driver).withTimeout(5, TimeUnit.SECONDS)
                                                                .pollingEvery(100, TimeUnit.MILLISECONDS)
                                                                .ignoring(NoSuchElementException.class);
        wait.until(new Function<WebDriver, WebElement>() {
            @Override
            public WebElement apply(WebDriver driver) {
                List<WebElement> elts = driver.findElements(
                        By.xpath("//div[contains(.,'A draft of this document has been saved')]"));
                if (!elts.isEmpty()) {
                    return elts.get(0);
                }
                return null;
            }
        });
    }

    /**
     * Delete created user and data.
     *
     * @throws UserNotConnectedException
     * @since 5.7.1
     */
    private void restoreState() throws Exception {
        UsersTabSubPage usersTab = login().getAdminCenter().getUsersGroupsHomePage().getUsersTab();
        usersTab = usersTab.searchUser(TEST_USERNAME);
        usersTab = usersTab.viewUser(TEST_USERNAME).deleteUser();
        DocumentBasePage documentBasePage = usersTab.exitAdminCenter()
                                                    .getHeaderLinks()
                                                    .getNavigationSubPage()
                                                    .goToDocument("Workspaces");
        deleteWorkspace(documentBasePage, WORKSPACE_TITLE);
        logout();
    }

    /**
     * Returns true if detected FF browser version is >= FF 14, to avoid running the test on browsers that do not
     * support localstorage.
     *
     * @return whether we run the test or not
     * @since 5.7.2
     */
    private boolean runTestForBrowser() {
        final String browser = driver.getCapabilities().getBrowserName();

        // Exclude too old version of firefox
        if (browser.equals("firefox")) {
            try {
                final String browserVersion = driver.getCapabilities().getVersion();
                final String[] versionAsArray = browserVersion.split("\\.");
                final int majorVersion = Integer.parseInt(versionAsArray[0]);
                if (majorVersion < 14) {
                    return false;
                }
            } catch (Exception e) {

            }
        }
        return true;
    }

    /**
     * This methods checks that once a simple html input is changed within a page, the new value is stored in the
     * browser local storage in case of accidental loose (crash, freeze, network failure). The value can then be
     * restored from the local storage when re-editing the page afterwards.
     *
     * @since 5.7.1
     */
    @Test
    public void testAutoSaveOnChangeAndRestore() throws Exception {

        if (!runTestForBrowser()) {
            log.warn("Browser not supported. Nothing to run.");
            return;
        }

        prepare();

        DocumentBasePage documentBasePage;
        WebElement descriptionElt, titleElt;

        // Log as test user and edit the created workdspace
        documentBasePage = login(TEST_USERNAME, TEST_PASSWORD).getContentTab()
                                                              .goToDocument("Workspaces")
                                                              .getContentTab()
                                                              .goToDocument(WORKSPACE_TITLE);
        documentBasePage.getEditTab();

        LocalStorage localStorage = new LocalStorage(driver);
        localStorage.clearLocalStorage();
        String currentDocumentId = getCurrentDocumentId();

        descriptionElt = driver.findElement(By.name(DESCRIPTION_ELT_ID));
        titleElt = driver.findElement(By.name(TITLE_ELT_ID));
        log.debug("1 - " + localStorage.getLocalStorageLength());

        // We change the value of the title
        Keys ctrlKey = Keys.CONTROL;
        if (Platform.MAC.equals(driver.getCapabilities().getPlatform())) {
            ctrlKey = Keys.COMMAND;
        }
        titleElt.click();
        titleElt.sendKeys(Keys.chord(ctrlKey, "a") + Keys.DELETE + NEW_WORKSPACE_TITLE);
        // weird thing in webdriver: we need to call clear on an input of the
        // form to fire an onchange event
        descriptionElt.click();
        descriptionElt.clear();
        log.debug("2 - " + localStorage.getLocalStorageLength());

        // Now must have something saved in the localstorage
        String lsItem = localStorage.getItemFromLocalStorage(currentDocumentId);
        final String lookupString = "\"" + TITLE_ELT_ID + "\":\"" + NEW_WORKSPACE_TITLE + "\"";

        assertTrue(lsItem != null && lsItem.length() > 0);
        assertTrue(lsItem.contains(lookupString));

        // Let's leave the document page with unsaved changes and check the popup
        log.debug("3 - " + localStorage.getLocalStorageLength());
        driver.findElement(By.linkText("Sections")).click();
        byPassLeavePagePopup(false);
        log.debug("4 - " + localStorage.getLocalStorageLength());

        // Let's leave the edit tab of the workspace with unsaved changes. A
        // popup should also prevent us from doing that
        if (documentBasePage.useAjaxTabs()) {
            AjaxRequestManager arm = new AjaxRequestManager(driver);
            arm.begin();
            documentBasePage.clickOnDocumentTabLink(documentBasePage.contentTabLink, false);
            byPassLeaveTabPopup(true);
            arm.end();
        } else {
            documentBasePage.clickOnDocumentTabLink(documentBasePage.contentTabLink, false);
            byPassLeavePagePopup(true);
        }

        // Get back to edit tab. Since we didn't save, the title must be the initial one.
        documentBasePage = asPage(DocumentBasePage.class);
        documentBasePage.getEditTab();
        localStorage = new LocalStorage(driver);
        titleElt = Locator.findElementWithTimeout(By.name(TITLE_ELT_ID));
        String titleEltValue = titleElt.getAttribute("value");
        assertEquals(WORKSPACE_TITLE, titleEltValue);
        log.debug("5 - " + localStorage.getLocalStorageLength());

        // We must find in the localstorage an entry matching the previous
        // document which contains the title we edited
        lsItem = localStorage.getItemFromLocalStorage(currentDocumentId);
        assertTrue(lsItem.contains(lookupString));
        log.debug("6 - " + localStorage.getLocalStorageLength());

        checkSafeEditRestoreProvided();

        triggerSafeEditRestore();

        // We check that the title value has actually been restored
        titleElt = driver.findElement(By.name(TITLE_ELT_ID));
        titleEltValue = titleElt.getAttribute("value");
        assertEquals(NEW_WORKSPACE_TITLE, titleEltValue);

        // try to leave again
        if (documentBasePage.useAjaxTabs()) {
            AjaxRequestManager arm = new AjaxRequestManager(driver);
            arm.begin();
            documentBasePage.clickOnDocumentTabLink(documentBasePage.contentTabLink, false);
            byPassLeaveTabPopup(false);
            arm.end();
        } else {
            documentBasePage.clickOnDocumentTabLink(documentBasePage.contentTabLink, false);
            byPassLeavePagePopup(false);
        }

        driver.findElement(By.linkText("Sections")).click();
        byPassLeavePagePopup(true);

        logout();
        restoreState();
    }

    /**
     * Check that safeEdit also works on select2. We test is against Coverage.
     *
     * @throws Exception
     * @since 5.7.3
     */
    @Test
    public void testSafeEditOnSelect2() throws Exception {

        if (!runTestForBrowser()) {
            log.warn("Browser not supported. Nothing to run.");
            return;
        }

        prepare();

        DocumentBasePage documentBasePage;
        // Log as test user and edit the created workdspace
        documentBasePage = login(TEST_USERNAME, TEST_PASSWORD).getContentTab()
                                                              .goToDocument("Workspaces")
                                                              .getContentTab()
                                                              .goToDocument(WORKSPACE_TITLE);

        // Create test File
        FileDocumentBasePage filePage = createFile(documentBasePage, "Test file", "Test File description", false, null,
                null, null);
        EditTabSubPage editTabSubPage = filePage.getEditTab();

        Select2WidgetElement coverageWidget = new Select2WidgetElement(driver,
                driver.findElement(By.xpath("//*[@id='s2id_document_edit:nxl_dublincore:nxw_coverage_1_select2']")));
        coverageWidget.selectValue(COVERAGE);

        waitForSavedNotification();

        // We leave the page without saving, the safeEdit mechanism should be triggered
        if (documentBasePage.useAjaxTabs()) {
            AjaxRequestManager arm = new AjaxRequestManager(driver);
            arm.begin();
            documentBasePage.clickOnDocumentTabLink(documentBasePage.summaryTabLink, false);
            byPassLeaveTabPopup(true);
            arm.end();
        } else {
            documentBasePage.clickOnDocumentTabLink(documentBasePage.summaryTabLink, false);
            byPassLeavePagePopup(true);
        }

        filePage = asPage(FileDocumentBasePage.class);
        filePage.getEditTab();

        checkSafeEditRestoreProvided();

        triggerSafeEditRestore();

        waitForSavedNotification();

        WebElement savedCoverage = driver.findElement(By.xpath(ITSelect2Test.S2_COVERAGE_FIELD_XPATH));
        final String text = savedCoverage.getText();
        assertNotNull(text);
        assertTrue(text.endsWith(ITSelect2Test.COVERAGE));

        editTabSubPage.save();
        logout();

        restoreState();
    }

    private void waitForSavedNotification() {
        Wait<WebDriver> wait = new FluentWait<WebDriver>(driver).withTimeout(5, TimeUnit.SECONDS)
                                                                .pollingEvery(100, TimeUnit.MILLISECONDS)
                                                                .ignoring(NoSuchElementException.class);
        try {
            wait.until(new Function<WebDriver, WebElement>() {
                @Override
                public WebElement apply(WebDriver driver) {
                    return driver.findElement(By.xpath("//div[contains(.,'" + DRAFT_SAVE_TEXT_NOTIFICATION + "')]"));
                }
            });
        } catch (TimeoutException e) {
            log.warn("Could not see saved message, maybe I was too slow and it "
                    + "has already disappeared. Let's see if I can restore.");
        }
    }

    private void triggerSafeEditRestore() {
        // Let's restore
        WebElement confirmRestoreYes = driver.findElement(By.linkText("Use Draft"));
        // The following call randomly times out.
        // confirmRestoreYes.click();
        // We just want to trigger the js event handler attached to
        // confirmRestoreYes element. This is the workaround.
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", confirmRestoreYes);
    }

}
