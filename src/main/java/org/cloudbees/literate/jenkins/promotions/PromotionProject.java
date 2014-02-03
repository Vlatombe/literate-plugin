/*
 * The MIT License
 *
 * Copyright (c) 2009-2014, Kohsuke Kawaguchi, Stephen Connolly, CloudBees, Inc., and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.cloudbees.literate.jenkins.promotions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ParametersAction;
import hudson.model.PermalinkProjectAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import net.jcip.annotations.Immutable;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.jenkins.BuildEnvironment;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
@Immutable
public class PromotionProject
        extends AbstractProject<PromotionProject, PromotionBuild>
        implements Saveable, Describable<PromotionProject> {
    private static final Logger LOGGER = Logger.getLogger(PromotionProject.class.getName());
    @NonNull
    private final PromotionConfiguration configuration;

    public PromotionProject(ItemGroup owner, PromotionConfiguration configuration) {
        super(owner, configuration.getName());
        this.configuration = configuration;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDisplayName() {
        return StringUtils.defaultIfBlank(configuration.getDisplayName(), name);
    }

    @CheckForNull
    public String getEnvironment() {
        return configuration.getEnvironment();
    }

    @NonNull
    public PromotionConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns the root project value.
     *
     * @return the root project value.
     */
    @Override
    public LiterateBranchProject getRootProject() {
        return getParent().getOwner();
    }

    @Override
    public PromotionJobProperty getParent() {
        return (PromotionJobProperty) super.getParent();
    }

    /**
     * Gets the owner {@link LiterateBranchProject} that configured {@link PromotionJobProperty} as
     * a job property.
     */
    public LiterateBranchProject getOwner() {
        return getParent().getOwner();
    }

    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        return new DescribableList<Publisher, Descriptor<Publisher>>(this);
    }

    @Override
    protected Class<PromotionBuild> getBuildClass() {
        return PromotionBuild.class;
    }

    /**
     * Gets the textual representation of the assigned label as it was entered by the user.
     */
    @Override
    public String getAssignedLabelString() {
        // TODO properlt generate a label string
        Label assignedLabel = getAssignedLabel();
        return assignedLabel == null ? null : assignedLabel.getDisplayName();
    }

    @Override
    public Label getAssignedLabel() {
        // TODO properly parse the string to ensure quoting of environment segments is handled
        String environment = configuration.getEnvironment();
        if (StringUtils.isBlank(environment)) return null;
        List<String> environments = new ArrayList<String>();
        for (String s: environment.split("[,\\s]+")) {
            if (StringUtils.isNotBlank(s)) environments.add(s.trim());
        }
        if (environments.isEmpty()) return null;
        return getOwner().getParent().getEnvironmentMapper().getLabel(new BuildEnvironment(new HashSet<String>(environments)));
    }

    @Override
    public JDK getJDK() {
        return getOwner().getJDK();
    }

    /**
     * Get the icon name, without the extension. It will always return a non null
     * and non empty string, as <code>"star-gold"</code> is used for compatibility
     * for older promotions configurations.
     *
     * @return the icon name
     */
    public String getIcon() {
        return "star-gold";
    }

    public void promote(LiterateBranchBuild build, Cause cause, PromotionBadge... badges) throws IOException {
        promote2(build,cause,new PromotionStatus(this,Arrays.asList(badges)));
    }

    /**
     * Promote the given build by using the given qualification.
     *
     * @param cause
     *      Why the build is promoted?
     * @return
     *      Future to track the completion of the promotion.
     */
    public Future<PromotionBuild> promote2(LiterateBranchBuild build, Cause cause, PromotionStatus qualification) throws IOException {
        PromotionBranchBuildAction  a = build.getAction(PromotionBranchBuildAction.class);
        // build is qualified for a promotion.
        if(a!=null) {
            a.add(qualification);
        } else {
            build.addAction(new PromotionBranchBuildAction(build,qualification));
            build.save();
        }

        // schedule promotion activity.
        return scheduleBuild2(build,cause);
    }

    /**
     * @deprecated You need to be using {@link #scheduleBuild(LiterateBranchBuild)}
     */
    public boolean scheduleBuild() {
        return super.scheduleBuild();
    }

    public boolean scheduleBuild(LiterateBranchBuild build) {
        return scheduleBuild(build, new Cause.LegacyCodeCause());
    }

    /**
     * @deprecated Use {@link #scheduleBuild2(LiterateBranchBuild, Cause)}
     */
    public boolean scheduleBuild(LiterateBranchBuild build, Cause cause) {
        return scheduleBuild2(build, cause) != null;
    }

    public Future<PromotionBuild> scheduleBuild2(LiterateBranchBuild build, Cause cause) {
        assert build.getProject() == getOwner();

        // Get the parameters, if any, used in the target build and make these
        // available as part of the promotion steps
        List<ParametersAction> parameters = build.getActions(ParametersAction.class);

        // Create list of actions to pass to scheduled build
        List<Action> actions = new ArrayList<Action>();
        actions.addAll(parameters);
        actions.add(new PromotionTargetAction(build));

        // remember what build we are promoting
        return super.scheduleBuild2(0, cause, actions.toArray(new Action[actions.size()]));
    }

    public boolean isInQueue(LiterateBranchBuild build) {
        for (Queue.Item item : Jenkins.getInstance().getQueue().getItems(this)) {
            if (item.getAction(PromotionTargetAction.class).resolve(this) == build) {
                return true;
            }
        }
        return false;
    }

    //
    // these are dummy implementations to implement abstract methods.
    // need to think about what the implications are.
    //
    public boolean isFingerprintConfigured() {
        throw new UnsupportedOperationException();
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        throw new UnsupportedOperationException();
    }

    public PermalinkProjectAction.Permalink asPermalink() {
        return new PermalinkProjectAction.Permalink() {
            @Override
            public String getDisplayName() {
                return Messages.LiteratePromotionsBranchProperty_PermalinkDisplayName(
                        PromotionProject.this.getDisplayName());
            }

            @Override
            public String getId() {
                return PromotionProject.this.getName();
            }

            @Override
            public Run<?, ?> resolve(Job<?, ?> job) {
                String id = getId();
                for (Run<?, ?> build : job.getBuilds()) {
                    PromotionBranchBuildAction a = build.getAction(PromotionBranchBuildAction.class);
                    if (a != null && a.contains(id)) {
                        return build;
                    }
                }
                return null;
            }
        };
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Override
    public String getShortUrl() {
        // Must be overridden since PromotionJobProperty.getUrlChildPrefix is "" not "process" as you might expect
        // (also see e50f0f5 in 1.519)
        return "process/" + Util.rawEncode(getName()) + '/';
    }

    public boolean isActive() {
        return !isDisabled();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PromotionProject> {

        @Override
        public String getDisplayName() {
            return "Promotion process";
        }
    }
}
