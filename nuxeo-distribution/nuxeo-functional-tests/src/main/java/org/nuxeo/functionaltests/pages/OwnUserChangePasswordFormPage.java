/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Maxime Hilaire
 */
package org.nuxeo.functionaltests.pages;

import org.nuxeo.functionaltests.Required;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Change password sub tab page (From profile page)
 *
 * @since 7.3
 */
public class OwnUserChangePasswordFormPage extends AbstractPage {

    @Required
    @FindBy(id = "editUserPassword:nxl_user_2:nxw_passwordMatcher_firstPassword")
    WebElement firstPasswordInput;

    @Required
    @FindBy(id = "editUserPassword:nxl_user_2:nxw_passwordMatcher_secondPassword")
    WebElement secondPasswordInput;

    @Required
    @FindBy(xpath = "//input[@value=\"Save\"]")
    WebElement saveButton;

    public OwnUserChangePasswordFormPage(WebDriver driver) {
        super(driver);
    }

    public OwnUserChangePasswordFormPage changePassword(String password) {
        firstPasswordInput.clear();
        firstPasswordInput.sendKeys(password);
        secondPasswordInput.clear();
        secondPasswordInput.sendKeys(password);
        saveButton.click();
        return asPage(OwnUserChangePasswordFormPage.class);
    }

}
