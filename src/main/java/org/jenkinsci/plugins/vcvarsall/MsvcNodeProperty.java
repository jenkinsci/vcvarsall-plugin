package org.jenkinsci.plugins.vcvarsall;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Property for configuring Visual Studio support
 */
public class MsvcNodeProperty extends NodeProperty<Node> {

    private String mVersion;
    private String mArch;

    @DataBoundConstructor
    public MsvcNodeProperty(String version, String arch) {
        mVersion = version;
        mArch = arch;
    }

    @SuppressWarnings("WeakerAccess")
    public String getVersion() {
        return mVersion;
    }

    @SuppressWarnings("WeakerAccess")
    public String getArch() {
        return mArch;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "Run vcvarsall.bat on startup";
        }

        public ListBoxModel doFillVersionItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Visual Studio 2013", "13.0");
            items.add("Visual Studio 2015", "14.0");
            return items;
        }

        public ListBoxModel doFillArchItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("x86", "x86");
            items.add("x86_64", "x86_64");
            return items;
        }
    }
}
