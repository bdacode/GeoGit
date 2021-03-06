/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */

package org.geogit.cli.plumbing;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geogit.api.GeoGIT;
import org.geogit.api.NodeRef;
import org.geogit.api.RevFeature;
import org.geogit.api.RevFeatureType;
import org.geogit.api.RevObject;
import org.geogit.api.plumbing.DiffFeature;
import org.geogit.api.plumbing.RevObjectParse;
import org.geogit.api.plumbing.diff.AttributeDiff;
import org.geogit.api.plumbing.diff.AttributeDiff.TYPE;
import org.geogit.api.plumbing.diff.DiffEntry;
import org.geogit.api.plumbing.diff.DiffEntry.ChangeType;
import org.geogit.api.plumbing.diff.FeatureDiff;
import org.geogit.cli.AbstractCommand;
import org.geogit.cli.CLICommand;
import org.geogit.cli.CommandFailedException;
import org.geogit.cli.GeogitCLI;
import org.geogit.storage.text.TextValueSerializer;
import org.opengis.feature.type.PropertyDescriptor;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Plumbing command to shows changes between trees
 * 
 * @see DiffTree
 */
@Parameters(commandNames = "diff-tree", commandDescription = "Show changes between trees")
public class DiffTree extends AbstractCommand implements CLICommand {

    private static final String LINE_BREAK = System.getProperty("line.separator");

    @Parameter(description = "[<treeish> [<treeish>]] [-- <path>...]", arity = 2)
    private List<String> refSpec = Lists.newArrayList();

    @Parameter(names = "--", hidden = true, variableArity = true)
    private List<String> paths = Lists.newArrayList();

    @Parameter(names = "--describe", description = "add description of versions for each modified element")
    private boolean describe;

    /**
     * Executes the diff-tree command with the specified options.
     */
    @Override
    protected void runInternal(GeogitCLI cli) throws IOException {
        if (refSpec.size() > 2) {
            throw new CommandFailedException("Tree refspecs list is too long :" + refSpec);
        }

        GeoGIT geogit = cli.getGeogit();

        org.geogit.api.plumbing.DiffTree diff = geogit
                .command(org.geogit.api.plumbing.DiffTree.class);

        String oldVersion = resolveOldVersion();
        String newVersion = resolveNewVersion();

        diff.setOldVersion(oldVersion).setNewVersion(newVersion);

        Iterator<DiffEntry> diffEntries;
        if (paths.isEmpty()) {
            diffEntries = diff.setProgressListener(cli.getProgressListener()).call();
        } else {
            diffEntries = Iterators.emptyIterator();
            for (String path : paths) {
                Iterator<DiffEntry> moreEntries = diff.setFilterPath(path)
                        .setProgressListener(cli.getProgressListener()).call();
                diffEntries = Iterators.concat(diffEntries, moreEntries);
            }
        }

        DiffEntry diffEntry;
        while (diffEntries.hasNext()) {
            diffEntry = diffEntries.next();
            StringBuilder sb = new StringBuilder();
            String path = diffEntry.newPath() != null ? diffEntry.newPath() : diffEntry.oldPath();
            if (describe) {
                sb.append(diffEntry.changeType().toString().charAt(0)).append(' ').append(path)
                        .append(LINE_BREAK);

                if (diffEntry.changeType() == ChangeType.MODIFIED) {
                    FeatureDiff featureDiff = geogit.command(DiffFeature.class)
                            .setNewVersion(Suppliers.ofInstance(diffEntry.getNewObject()))
                            .setOldVersion(Suppliers.ofInstance(diffEntry.getOldObject())).call();
                    Map<PropertyDescriptor, AttributeDiff> diffs = featureDiff.getDiffs();
                    HashSet<PropertyDescriptor> diffDescriptors = Sets.newHashSet(diffs.keySet());
                    NodeRef noderef = diffEntry.changeType() != ChangeType.REMOVED ? diffEntry
                            .getNewObject() : diffEntry.getOldObject();
                    RevFeatureType featureType = geogit.command(RevObjectParse.class)
                            .setObjectId(noderef.getMetadataId()).call(RevFeatureType.class).get();
                    Optional<RevObject> obj = geogit.command(RevObjectParse.class)
                            .setObjectId(noderef.objectId()).call();
                    RevFeature feature = (RevFeature) obj.get();
                    ImmutableList<Optional<Object>> values = feature.getValues();
                    ImmutableList<PropertyDescriptor> descriptors = featureType.sortedDescriptors();
                    int idx = 0;
                    for (PropertyDescriptor descriptor : descriptors) {
                        if (diffs.containsKey(descriptor)) {
                            AttributeDiff ad = diffs.get(descriptor);
                            sb.append(ad.getType().toString().charAt(0) + " "
                                    + descriptor.getName().toString() + LINE_BREAK);
                            if (!ad.getType().equals(TYPE.ADDED)) {
                                Object value = ad.getOldValue().orNull();
                                sb.append(TextValueSerializer.asString(Optional.fromNullable(value)));
                                sb.append(LINE_BREAK);
                            }
                            if (!ad.getType().equals(TYPE.REMOVED)) {
                                Object value = ad.getNewValue().orNull();
                                sb.append(TextValueSerializer.asString(Optional.fromNullable(value)));
                                sb.append(LINE_BREAK);
                            }
                            diffDescriptors.remove(descriptor);
                        } else {
                            sb.append("U ").append(descriptor.getName().toString())
                                    .append(LINE_BREAK);
                            sb.append(TextValueSerializer.asString(values.get(idx))).append(
                                    LINE_BREAK);
                        }
                        idx++;
                    }
                    for (PropertyDescriptor descriptor : diffDescriptors) {
                        AttributeDiff ad = diffs.get(descriptor);
                        sb.append(ad.getType().toString().charAt(0) + " "
                                + descriptor.getName().toString() + LINE_BREAK);
                        if (!ad.getType().equals(TYPE.ADDED)) {
                            Object value = ad.getOldValue().orNull();
                            sb.append(TextValueSerializer.asString(Optional.fromNullable(value)));
                            sb.append(LINE_BREAK);
                        }
                        if (!ad.getType().equals(TYPE.REMOVED)) {
                            Object value = ad.getNewValue().orNull();
                            sb.append(TextValueSerializer.asString(Optional.fromNullable(value)));
                            sb.append(LINE_BREAK);
                        }
                    }
                } else {
                    NodeRef noderef = diffEntry.changeType() == ChangeType.ADDED ? diffEntry
                            .getNewObject() : diffEntry.getOldObject();
                    RevFeatureType featureType = geogit.command(RevObjectParse.class)
                            .setObjectId(noderef.getMetadataId()).call(RevFeatureType.class).get();
                    Optional<RevObject> obj = geogit.command(RevObjectParse.class)
                            .setObjectId(noderef.objectId()).call();
                    RevFeature feature = (RevFeature) obj.get();
                    ImmutableList<Optional<Object>> values = feature.getValues();
                    int i = 0;
                    for (Optional<Object> value : values) {
                        sb.append(diffEntry.changeType().toString().charAt(0));
                        sb.append(' ');
                        sb.append(featureType.sortedDescriptors().get(i).getName().toString());
                        sb.append(LINE_BREAK);
                        sb.append(TextValueSerializer.asString(value));
                        sb.append(LINE_BREAK);
                        i++;
                    }
                    sb.append(LINE_BREAK);
                }

                sb.append(LINE_BREAK);
            } else {
                sb.append(path).append(' ');
                sb.append(diffEntry.oldObjectId().toString());
                sb.append(' ');
                sb.append(diffEntry.newObjectId().toString());
            }
            cli.getConsole().println(sb.toString());
        }
    }

    private String resolveOldVersion() {
        return refSpec.size() > 0 ? refSpec.get(0) : "WORK_HEAD";
    }

    private String resolveNewVersion() {
        return refSpec.size() > 1 ? refSpec.get(1) : "STAGE_HEAD";
    }

}
