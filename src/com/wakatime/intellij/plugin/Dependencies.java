/* ==========================================================
File:        Dependencies.java
Description: Manages plugin dependencies.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Dependencies {

    private static String pythonLocation = null;
    private static String resourcesLocation = null;

    public static boolean isPythonInstalled() {
        return Dependencies.getPythonLocation() != null;
    }

    public static String getResourcesLocation() {
        if (Dependencies.resourcesLocation == null) {
            String separator = "[\\\\/]";
            Dependencies.resourcesLocation = WakaTime.class.getResource("WakaTime.class").getPath()
                    .replaceFirst("file:", "")
                    .replaceAll("%20", " ")
                    .replaceFirst("com" + separator + "wakatime" + separator + "intellij" + separator + "plugin" + separator + "WakaTime.class", "")
                    .replaceFirst("WakaTime.jar!" + separator, "") + "WakaTime-resources";
            if (System.getProperty("os.name").startsWith("Windows") && Dependencies.resourcesLocation.startsWith("/")) {
                Dependencies.resourcesLocation = Dependencies.resourcesLocation.substring(1);
            }
        }
        return Dependencies.resourcesLocation;
    }

    public static String getPythonLocation() {
        if (Dependencies.pythonLocation != null)
            return Dependencies.pythonLocation;
        ArrayList<String> paths = new ArrayList<String>();
        paths.add(null);
        paths.add("/");
        paths.add("/usr/local/bin/");
        paths.add("/usr/bin/");
        if (System.getProperty("os.name").contains("Windows")) {
            File resourcesLocation = new File(Dependencies.getResourcesLocation());
            paths.add(combinePaths(resourcesLocation.getAbsolutePath(), "python"));
            paths.add(getPythonFromRegistry(WinReg.HKEY_CURRENT_USER));
            paths.add(getPythonFromRegistry(WinReg.HKEY_LOCAL_MACHINE));
            paths.add("/python39");
            paths.add("/Python39");
            paths.add("/python38");
            paths.add("/Python38");
            paths.add("/python37");
            paths.add("/Python37");
            paths.add("/python36");
            paths.add("/Python36");
            paths.add("/python35");
            paths.add("/Python35");
            paths.add("/python34");
            paths.add("/Python34");
            paths.add("/python33");
            paths.add("/Python33");
            paths.add("/python27");
            paths.add("/Python27");
            paths.add("/python26");
            paths.add("/Python26");
        }
        for (String path : paths) {
            try {
                String[] cmds = {combinePaths(path, "pythonw"), "--version"};
                Runtime.getRuntime().exec(cmds);
                Dependencies.pythonLocation = combinePaths(path, "pythonw");
                break;
            } catch (Exception e) {
                try {
                    String[] cmds = {combinePaths(path, "python"), "--version"};
                    Runtime.getRuntime().exec(cmds);
                    Dependencies.pythonLocation = combinePaths(path, "python");
                    break;
                } catch (Exception e2) { }
            }
        }
        if (Dependencies.pythonLocation != null) {
            WakaTime.log.debug("Found python binary: " + Dependencies.pythonLocation);
        } else {
            WakaTime.log.warn("Could not find python binary.");
        }
        return Dependencies.pythonLocation;
    }

    public static String getPythonFromRegistry(WinReg.HKEY hkey) {
        String path = null;
        if (System.getProperty("os.name").contains("Windows")) {
            try {
                String key = "Software\\\\Wow6432Node\\\\Python\\\\PythonCore";
                for (String version : Advapi32Util.registryGetKeys(hkey, key)) {
                    path = Advapi32Util.registryGetStringValue(hkey, key + "\\" + version + "\\InstallPath", "");
                    if (path != null) {
                        break;
                    }
                }
            }  catch (Exception e) {
                WakaTime.log.debug(e);
            }
            if (path == null) {
                try {
                    String key = "Software\\\\Python\\\\PythonCore";
                    for (String version : Advapi32Util.registryGetKeys(hkey, key)) {
                        path = Advapi32Util.registryGetStringValue(hkey, key + "\\" + version + "\\InstallPath", "");
                        if (path != null) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    WakaTime.log.debug(e);
                }
            }
        }
        return path;
    }

    public static boolean isCLIInstalled() {
        File cli = new File(Dependencies.getCLILocation());
        WakaTime.log.debug("WakaTime Core Location: " + cli.getAbsolutePath());
        WakaTime.log.debug("WakaTime Core Exists: " + cli.exists());
        return cli.exists();
    }

    public static boolean isCLIOld() {
        if (!Dependencies.isCLIInstalled()) {
            return false;
        }
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getPythonLocation());
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--version");
        try {
            Process p = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            p.waitFor();
            String output = "";
            String s;
            while ((s = stdInput.readLine()) != null) {
                output += s;
            }
            while ((s = stdError.readLine()) != null) {
                output += s;
            }
            WakaTime.log.debug("wakatime cli version check output: \"" + output + "\"");
            WakaTime.log.debug("wakatime cli version check exit code: " + p.exitValue());

            if (p.exitValue() == 0) {
                String cliVersion = latestCliVersion();
                WakaTime.log.debug("Current cli version from GitHub: " + cliVersion);
                if (output.contains(cliVersion))
                    return false;
            }
        } catch (Exception e) { }
        return true;
    }

    public static String latestCliVersion() {
        String url = "https://raw.githubusercontent.com/wakatime/wakatime/master/wakatime/__about__.py";
        String aboutText = getUrlAsString(url);
        Pattern p = Pattern.compile("__version_info__ = \\('([0-9]+)', '([0-9]+)', '([0-9]+)'\\)");
        Matcher m = p.matcher(aboutText);
        if (m.find()) {
            return m.group(1) + "." + m.group(2) + "." + m.group(3);
        }
        return "Unknown";
    }

    public static String getCLILocation() {
        return combinePaths(Dependencies.getResourcesLocation(), "wakatime-master", "wakatime", "cli.py");
    }

    public static void installCLI() {
        File cli = new File(Dependencies.getCLILocation());
        if (!cli.getParentFile().getParentFile().getParentFile().exists())
            cli.getParentFile().getParentFile().getParentFile().mkdirs();

        String url = "https://codeload.github.com/wakatime/wakatime/zip/master";
        String zipFile = combinePaths(cli.getParentFile().getParentFile().getParentFile().getAbsolutePath(), "wakatime-cli.zip");
        File outputDir = cli.getParentFile().getParentFile().getParentFile();

        // Delete old wakatime-master directory if it exists
        File dir = cli.getParentFile().getParentFile();
        if (dir.exists()) {
            deleteDirectory(dir);
        }

        // download wakatime-master.zip file
        if (downloadFile(url, zipFile)) {
            try {
                Dependencies.unzip(zipFile, outputDir);
                File oldZipFile = new File(zipFile);
                oldZipFile.delete();
            } catch (IOException e) {
                WakaTime.log.error(e);
            }
        }
    }

    public static void upgradeCLI() {
        Dependencies.installCLI();
    }

    public static void installPython() {
        if (System.getProperty("os.name").contains("Windows")) {
            String pyVer = "3.5.0";
            String arch = "win32";
            if (is64bit()) arch = "amd64";
            String url = "https://www.python.org/ftp/python/" + pyVer + "/python-" + pyVer + "-embed-" + arch + ".zip";

            File dir = new File(Dependencies.getResourcesLocation());
            File zipFile = new File(combinePaths(dir.getAbsolutePath(), "python.zip"));
            if (downloadFile(url, zipFile.getAbsolutePath())) {

                File targetDir = new File(combinePaths(dir.getAbsolutePath(), "python"));

                // extract python
                try {
                    Dependencies.unzip(zipFile.getAbsolutePath(), targetDir);
                } catch (IOException e) {
                    WakaTime.log.error(e);
                }
                zipFile.delete();
            }
        }
    }

    public static boolean downloadFile(String url, String saveAs) {
        File outFile = new File(saveAs);

        // create output directory if does not exist
        File outDir = outFile.getParentFile();
        if (!outDir.exists())
            outDir.mkdirs();

        URL downloadUrl = null;
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException e) { }

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            rbc = Channels.newChannel(downloadUrl.openStream());
            fos = new FileOutputStream(saveAs);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            return true;
        } catch (RuntimeException e) {
            WakaTime.log.error(e);
            try {
                // try downloading without verifying SSL cert (https://github.com/wakatime/jetbrains-wakatime/issues/46)
                SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
                SSL_CONTEXT.init(null, new TrustManager[] { new LocalSSLTrustManager() }, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
                HttpsURLConnection conn = (HttpsURLConnection)downloadUrl.openConnection();
                InputStream inputStream = conn.getInputStream();
                fos = new FileOutputStream(saveAs);
                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                fos.close();
                return true;
            } catch (NoSuchAlgorithmException e1) {
                WakaTime.log.error(e1);
            } catch (KeyManagementException e1) {
                WakaTime.log.error(e1);
            } catch (IOException e1) {
                WakaTime.log.error(e1);
            }
        } catch (IOException e) {
            WakaTime.log.error(e);
        }

        return false;
    }

    public static String getUrlAsString(String url) {
        StringBuilder text = new StringBuilder();

        URL downloadUrl = null;
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException e) { }

        try {
            InputStream inputStream = downloadUrl.openStream();
            byte[] buffer = new byte[4096];
            while (inputStream.read(buffer) != -1) {
                text.append(new String(buffer, "UTF-8"));
            }
            inputStream.close();
        } catch (RuntimeException e) {
            WakaTime.log.error(e);
            try {
                // try downloading without verifying SSL cert (https://github.com/wakatime/jetbrains-wakatime/issues/46)
                SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
                SSL_CONTEXT.init(null, new TrustManager[] { new LocalSSLTrustManager() }, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
                HttpsURLConnection conn = (HttpsURLConnection)downloadUrl.openConnection();
                InputStream inputStream = conn.getInputStream();
                byte[] buffer = new byte[4096];
                while (inputStream.read(buffer) != -1) {
                    text.append(new String(buffer, "UTF-8"));
                }
                inputStream.close();
            } catch (NoSuchAlgorithmException e1) {
                WakaTime.log.error(e1);
            } catch (KeyManagementException e1) {
                WakaTime.log.error(e1);
            } catch (IOException e1) {
                WakaTime.log.error(e1);
            }
        } catch (IOException e) {
            WakaTime.log.error(e);
        }

        return text.toString();
    }

    private static void unzip(String zipFile, File outputDir) throws IOException {
        if(!outputDir.exists())
            outputDir.mkdirs();

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry ze = zis.getNextEntry();

        while (ze != null) {
            String fileName = ze.getName();
            File newFile = new File(outputDir, fileName);

            if (ze.isDirectory()) {
                newFile.mkdirs();
            } else {
                FileOutputStream fos = new FileOutputStream(newFile.getAbsolutePath());
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }

            ze = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }

    private static void deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        path.delete();
    }

    public static boolean is64bit() {
        boolean is64bit = false;
        if (System.getProperty("os.name").contains("Windows")) {
            is64bit = (System.getenv("ProgramFiles(x86)") != null);
        } else {
            is64bit = (System.getProperty("os.arch").indexOf("64") != -1);
        }
        return is64bit;
    }

    private static String combinePaths(String... args) {
        File path = null;
        for (String arg : args) {
            if (arg != null) {
                if (path == null)
                    path = new File(arg);
                else
                    path = new File(path, arg);
            }
        }
        if (path == null)
            return null;
        return path.toString();
    }
}
