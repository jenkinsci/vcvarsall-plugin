package org.jenkinsci.plugins.vcvarsall;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Property for configuring Visual Studio support
 */
public class MsvcNodeProperty extends NodeProperty<Node> {

    static final String X86 = "x86";
    static final String X86_64 = "x86_64";

    private String mVersion;
    private String mArch;
    private EnvVars mEnvVars;

    @DataBoundConstructor
    public MsvcNodeProperty(String version, String arch) {
        mVersion = version;
        mArch = arch;
    }

    void setEnvVars(EnvVars envVars) {
        mEnvVars = envVars;
    }

    @SuppressWarnings("WeakerAccess")
    public String getVersion() {
        return mVersion;
    }

    @SuppressWarnings("WeakerAccess")
    public String getArch() {
        return mArch;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return Environment.create(mEnvVars);
    }

    @Override
    public void buildEnvVars(@Nonnull EnvVars env, @Nonnull TaskListener listener) {
        env.putAll(mEnvVars);
    }

    @Override
    public NodeProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        NodeProperty<?> nodeProperty = super.reconfigure(req, form);
        if (nodeProperty != null) {
            ((MsvcNodeProperty) nodeProperty).setEnvVars(mEnvVars);
        }
        return nodeProperty;
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
            items.add("Visual Studio 2013", "12.0");
            items.add("Visual Studio 2015", "14.0");
            return items;
        }

        public ListBoxModel doFillArchItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("x86", X86);
            items.add("x86_64", X86_64);
            return items;
        }
    }
}
