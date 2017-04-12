package org.jenkinsci.plugins.vcvarsall;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import jenkins.security.MasterToSlaveCallable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

/**
 * Listens for computers coming online and if they are configured to provide
 * MSVC compilers, runs the appropriate vcvarsall script on them.
 */
@Extension
@SuppressWarnings("unused")
public class ComputerListenerImpl extends ComputerListener {

    /**
     * Run vcvarsall with the correct parameters
     */
    private static class RunVcvarsallCallable
            extends MasterToSlaveCallable<Object, RuntimeException> {

        private String mVersion;
        private String mArch;

        RunVcvarsallCallable(String version, String arch) {
            mVersion = version;
            mArch = arch;
        }

        /**
         * Determine if the host is running a 64-bit operating system
         * @return true if the host is 64-bit
         */
        private boolean isHost64Bit() {
            return System.getenv("ProgramFiles(x86)") != null;
        }

        /**
         * Find the installation directory for Visual Studio
         * @return absolute path to the installation directory or NULL if non-existent
         * @throws RuntimeException for invalid versions
         */
        private String findInstallDir() throws RuntimeException {
            String installKey;
            if (isHost64Bit()) {
                installKey = "SOFTWARE\\Wow6432Node\\Microsoft\\VisualStudio\\SxS\\VS7";
            } else {
                installKey = "SOFTWARE\\Microsoft\\VisualStudio\\SxS\\VS7";
            }
            String installDir;
            try {
                installDir = WinRegistry.readString(
                        WinRegistry.HKEY_LOCAL_MACHINE,
                        installKey,
                        mVersion
                );
            } catch (IllegalAccessException|InvocationTargetException e) {
                throw new RuntimeException(e.getMessage());
            }
            if (installDir == null) {
                throw new RuntimeException("unable to find installation directory");
            }
            return installDir;
        }

        /**
         * Find the absolute path to vcvarsall.bat
         * @param installDir Visual Studio installation directory
         * @return absolute path to the vcvarsall script
         * @throws RuntimeException for invalid versions
         */
        private String findVcvarsall(String installDir) throws RuntimeException {
            String path;
            switch (mVersion) {
                case MsvcNodeProperty.MSVC2013:
                case MsvcNodeProperty.MSVC2015:
                    path = installDir + "VC\\vcvarsall.bat";
                    break;
                case MsvcNodeProperty.MSVC2017:
                    path = installDir + "VC\\Auxiliary\\Build\\vcvarsall.bat";
                    break;
                default:
                    throw new RuntimeException("invalid Visual Studio version");
            }
            if (!new File(path).exists()) {
                throw new RuntimeException("vcvarsall.bat does not exist");
            }
            return path;
        }

        /**
         * Generate the parameter to pass to vcvarsall.bat
         * @return parameter to pass
         * @throws RuntimeException for invalid arch
         */
        private String generateParam() throws RuntimeException {
            boolean use64Bit = isHost64Bit();
            if (mVersion.equals(MsvcNodeProperty.MSVC2013)) {
                use64Bit = false;
            }
            switch (mArch) {
                case MsvcNodeProperty.X86:
                    return use64Bit ? "amd64_x86" : "x86";
                case MsvcNodeProperty.X86_64:
                    return use64Bit ? "amd64" : "x86_amd64";
                default:
                    throw new RuntimeException("invalid architecture");
            }
        }

        /**
         * Run the vcvarsall.bat script
         * @param path absolute path to vcvarsall
         * @param param parameter for passing to the script
         * @throws RuntimeException for failure to run the script
         */
        private EnvVars runVcvarsall(String path, String param) throws RuntimeException {
            EnvVars envVars = new EnvVars();
            try {
                ProcessBuilder builder = new ProcessBuilder(
                        "cmd", "/c",
                        String.format("\"%s\" > NUL %s && set", path, param)
                );
                Process process = builder.start();
                BufferedReader outReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );
                BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream())
                );
                StringBuilder errOutput = new StringBuilder();
                String out, err = null;
                while ((out = outReader.readLine()) != null || (err = errReader.readLine()) != null) {
                    if (out != null) {
                        out = out.trim();
                        if (out.length() != 0) {
                            envVars.addLine(out);
                        }
                    } else {
                        errOutput.append(err);
                    }
                }
                if (process.waitFor() != 0) {
                    throw new RuntimeException(errOutput.toString());
                }
            } catch(IOException|InterruptedException e) {
                throw new RuntimeException(e.getMessage());
            }
            return envVars;
        }

        @Override
        public Object call() throws RuntimeException {
            if (!System.getProperty("os.name").startsWith("Windows")) {
                throw new RuntimeException("host is not running Windows");
            }
            String path = findVcvarsall(findInstallDir());
            String param = generateParam();
            return runVcvarsall(path, param);
        }
    }

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
        super.preOnline(c, channel, root, listener);

        // Ignore any nodes that don't have the property
        MsvcNodeProperty msvcNodeProperty = c.getNode().getNodeProperties().get(MsvcNodeProperty.class);
        if (msvcNodeProperty == null) {
            return;
        }

        listener.getLogger().printf(
                "Preparing to run vcvarsall for VS %s (%s)\n",
                msvcNodeProperty.getVersion(),
                msvcNodeProperty.getArch()
        );

        // Retrieve all of the environment variables from vcvarsall
        EnvVars envVars;
        try {
            envVars = (EnvVars) channel.call(new RunVcvarsallCallable(
                    msvcNodeProperty.getVersion(),
                    msvcNodeProperty.getArch()
            ));
        } catch (RuntimeException e) {
            listener.getLogger().printf("ERROR: %s\n", e.getMessage());
            return;
        }

        // Pass the variables to the node
        msvcNodeProperty.setEnvVars(envVars);

        listener.getLogger().printf(
                "Received %d env. variables from vcvarsall\n",
                envVars.size()
        );
    }
}
