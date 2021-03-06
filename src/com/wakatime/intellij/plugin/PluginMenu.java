/* ==========================================================
File:        PluginMenu.java
Description: Adds a WakaTime item to the File menu.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public class PluginMenu extends AnAction {
    public PluginMenu() {
        super("WakaTime API Key");
        // super("WakaTime API Key", "", IconLoader.getIcon("/Mypackage/icon.png"));
    }
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        ApiKey apiKey = new ApiKey(project);
        apiKey.promptForApiKey();
    }
}
