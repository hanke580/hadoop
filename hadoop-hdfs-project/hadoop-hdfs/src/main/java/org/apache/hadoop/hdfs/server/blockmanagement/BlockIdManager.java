/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.blockmanagement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.server.common.GenerationStamp;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import java.io.IOException;

/**
 * BlockIdManager allocates the generation stamps and the block ID. The
 * {@see FSNamesystem} is responsible for persisting the allocations in the
 * {@see EditLog}.
 */
public class BlockIdManager {

    /**
     * The global generation stamp for legacy blocks with randomly
     * generated block IDs.
     */
    private final GenerationStamp legacyGenerationStamp = new GenerationStamp();

    /**
     * The global generation stamp for this file system.
     */
    private final GenerationStamp generationStamp = new GenerationStamp();

    /**
     * Most recent global generation stamp as seen on Active NameNode.
     * Used by StandbyNode only.<p/>
     * StandbyNode does not update its global {@link #generationStamp} during
     * edits tailing. The global generation stamp on StandbyNode is updated
     * <ol><li>when the block with the next generation stamp is actually
     * received</li>
     * <li>during fail-over it is bumped to the last value received from the
     * Active NN through edits and stored as
     * {@link #impendingGenerationStamp}</li></ol>
     * The former helps to avoid a race condition with IBRs during edits tailing.
     * The latter guarantees that generation stamps are never reused by new
     * Active after fail-over.
     * <p/> See HDFS-14941 for more details.
     */
    private final GenerationStamp impendingGenerationStamp = new GenerationStamp();

    /**
     * The value of the generation stamp when the first switch to sequential
     * block IDs was made. Blocks with generation stamps below this value
     * have randomly allocated block IDs. Blocks with generation stamps above
     * this value had sequentially allocated block IDs. Read from the fsImage
     * (or initialized as an offset from the V1 (legacy) generation stamp on
     * upgrade).
     */
    private long legacyGenerationStampLimit;

    /**
     * The global block ID space for this file system.
     */
    private final SequentialBlockIdGenerator blockIdGenerator;

    public BlockIdManager(BlockManager blockManager) {
        this.legacyGenerationStampLimit = HdfsConstants.GRANDFATHER_GENERATION_STAMP;
        this.blockIdGenerator = new SequentialBlockIdGenerator(blockManager);
    }

    /**
     * Upgrades the generation stamp for the filesystem
     * by reserving a sufficient range for all existing blocks.
     * Should be invoked only during the first upgrade to
     * sequential block IDs.
     */
    public long upgradeLegacyGenerationStamp() {
        Preconditions.checkState(generationStamp.getCurrentValue() == GenerationStamp.LAST_RESERVED_STAMP);
        generationStamp.skipTo(legacyGenerationStamp.getCurrentValue() + HdfsServerConstants.RESERVED_LEGACY_GENERATION_STAMPS);
        legacyGenerationStampLimit = generationStamp.getCurrentValue();
        return generationStamp.getCurrentValue();
    }

    /**
     * Sets the generation stamp that delineates random and sequentially
     * allocated block IDs.
     *
     * @param stamp set generation stamp limit to this value
     */
    public void setLegacyGenerationStampLimit(long stamp) {
        Preconditions.checkState(legacyGenerationStampLimit == HdfsConstants.GRANDFATHER_GENERATION_STAMP);
        legacyGenerationStampLimit = stamp;
    }

    /**
     * Gets the value of the generation stamp that delineates sequential
     * and random block IDs.
     */
    public long getGenerationStampAtblockIdSwitch() {
        return legacyGenerationStampLimit;
    }

    @VisibleForTesting
    SequentialBlockIdGenerator getBlockIdGenerator() {
        return blockIdGenerator;
    }

    /**
     * Sets the maximum allocated block ID for this filesystem. This is
     * the basis for allocating new block IDs.
     */
    public void setLastAllocatedBlockId(long blockId) {
        blockIdGenerator.skipTo(blockId);
    }

    /**
     * Gets the maximum sequentially allocated block ID for this filesystem
     */
    public long getLastAllocatedBlockId() {
        return blockIdGenerator.getCurrentValue();
    }

    /**
     * Sets the current generation stamp for legacy blocks
     */
    public void setLegacyGenerationStamp(long stamp) {
        legacyGenerationStamp.setCurrentValue(stamp);
    }

    /**
     * Gets the current generation stamp for legacy blocks
     */
    public long getLegacyGenerationStamp() {
        return legacyGenerationStamp.getCurrentValue();
    }

    /**
     * Gets the current generation stamp for this filesystem
     */
    public void setGenerationStamp(long stamp) {
        generationStamp.setCurrentValue(stamp);
    }

    /**
     * Set the currently highest gen stamp from active. Used
     * by Standby only.
     * @param stamp new genstamp
     */
    public void setImpendingGenerationStamp(long stamp) {
        impendingGenerationStamp.setIfGreater(stamp);
    }

    /**
     * Set the current genstamp to the impending genstamp.
     */
    public void applyImpendingGenerationStamp() {
        setGenerationStampIfGreater(impendingGenerationStamp.getCurrentValue());
    }

    @VisibleForTesting
    public long getImpendingGenerationStamp() {
        return impendingGenerationStamp.getCurrentValue();
    }

    /**
     * Set genstamp only when the given one is higher.
     * @param stamp
     */
    public void setGenerationStampIfGreater(long stamp) {
        generationStamp.setIfGreater(stamp);
    }

    public long getGenerationStamp() {
        return generationStamp.getCurrentValue();
    }

    /**
     * Increments, logs and then returns the stamp
     */
    long nextGenerationStamp(boolean legacyBlock) throws IOException {
        return legacyBlock ? getNextLegacyGenerationStamp() : getNextGenerationStamp();
    }

    @VisibleForTesting
    long getNextLegacyGenerationStamp() throws IOException {
        long legacyGenStamp = legacyGenerationStamp.nextValue();
        if (legacyGenStamp >= legacyGenerationStampLimit) {
            // We ran out of generation stamps for legacy blocks. In practice, it
            // is extremely unlikely as we reserved 1T legacy generation stamps. The
            // result is that we can no longer append to the legacy blocks that
            // were created before the upgrade to sequential block IDs.
            throw new OutOfLegacyGenerationStampsException();
        }
        return legacyGenStamp;
    }

    @VisibleForTesting
    long getNextGenerationStamp() {
        return generationStamp.nextValue();
    }

    public long getLegacyGenerationStampLimit() {
        return legacyGenerationStampLimit;
    }

    /**
     * Determine whether the block ID was randomly generated (legacy) or
     * sequentially generated. The generation stamp value is used to
     * make the distinction.
     *
     * @return true if the block ID was randomly generated, false otherwise.
     */
    boolean isLegacyBlock(Block block) {
        return block.getGenerationStamp() < getLegacyGenerationStampLimit();
    }

    /**
     * Increments, logs and then returns the block ID
     */
    long nextBlockId() {
        return blockIdGenerator.nextValue();
    }

    boolean isGenStampInFuture(Block block) {
        if (isLegacyBlock(block)) {
            return block.getGenerationStamp() > getLegacyGenerationStamp();
        } else {
            return block.getGenerationStamp() > getGenerationStamp();
        }
    }

    void clear() {
        legacyGenerationStamp.setCurrentValue(GenerationStamp.LAST_RESERVED_STAMP);
        generationStamp.setCurrentValue(GenerationStamp.LAST_RESERVED_STAMP);
        getBlockIdGenerator().setCurrentValue(SequentialBlockIdGenerator.LAST_RESERVED_BLOCK_ID);
        legacyGenerationStampLimit = HdfsConstants.GRANDFATHER_GENERATION_STAMP;
    }
}
