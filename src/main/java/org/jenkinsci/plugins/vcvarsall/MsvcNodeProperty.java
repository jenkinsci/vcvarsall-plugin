package org.jenkinsci.plugins.vcvarsall;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Property for configuring Visual Studio support
 */
public class MsvcNodeProperty extends NodeProperty<Node> {

    public static final String MSVC2013 = "msvc2013";
    public static final String MSVC2015 = "msvc2015";

    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";

    private String mVersion;
    private String mArch;

    @DataBoundConstructor
    public MsvcNodeProperty(String version, String arch) {
        mVersion = version;
        mArch = arch;
    }

    String getVersion() {
        return mVersion;
    }

    String getArch() {
        return mArch;
    }

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "Run vcvarsall.bat on startup";
        }
    }
}
