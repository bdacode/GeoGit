/* Copyright (c) 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the LGPL 2.1 license, available at the root
 * application directory.
 */
package org.geogit.api.merge;

import org.geogit.api.AbstractGeoGitOp;
import org.geogit.api.Ref;
import org.geogit.repository.ConfigurationContext;
import org.geogit.repository.Repository;

/**
 * <p>
 * Base merge operation; loaded with default merge strategy FirstInMergeOp
 * </p>
 * 
 * @author jhudson
 * @since 1.2.0
 */
public class MergeOp extends AbstractGeoGitOp<MergeResult> {

    private Ref branch;
    private String comment;
    private IMergeOp mergeStrategy;

    public MergeOp(Repository repository) {
        super(repository);
        this.comment = "";
        this.mergeStrategy = (IMergeOp) ConfigurationContext.getInstance().getBean("mergeOp", new Object[]{repository});
    }

    /**
     * Override the default merge operation 
     * @param mergeStrategy
     * @return
     */
    public MergeOp setMergeStrategy(final IMergeOp mergeStrategy) {
    	this.mergeStrategy = mergeStrategy;
    	return this;
    }

    public MergeOp include(final Ref branch) {
        this.branch = branch;
        return this;
    }

    public MergeOp setComment(final String comment){
        this.comment = comment;
        return this;
    }

    public MergeResult call() throws Exception {
        if (this.mergeStrategy == null) {
        	return new MergeResult();
        }
    	
    	/**
         * Load up the merge strategy
         */
    	this.mergeStrategy.setComment(this.comment);
    	this.mergeStrategy.setRepository(getRepository());

    	if (this.branch == null) {
    		throw new NoBranchToMergeException("There is no branch set to merge from, exiting - nothing to merge");
    	}
    	this.mergeStrategy.setBranch(this.branch);

        return this.mergeStrategy.call();
    }
}