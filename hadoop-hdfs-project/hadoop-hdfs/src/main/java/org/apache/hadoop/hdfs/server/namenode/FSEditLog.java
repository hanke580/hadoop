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
package org.apache.hadoop.hdfs.server.namenode;

import static org.apache.hadoop.util.ExitUtil.terminate;
import static org.apache.hadoop.util.Time.monotonicNow;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Options;
import org.apache.hadoop.fs.XAttr;
import org.apache.hadoop.fs.permission.AclEntry;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.fs.StorageType;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.CacheDirectiveInfo;
import org.apache.hadoop.hdfs.protocol.CachePoolInfo;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants.NamenodeRole;
import org.apache.hadoop.hdfs.server.common.Storage.FormatConfirmable;
import org.apache.hadoop.hdfs.server.common.Storage.StorageDirectory;
import org.apache.hadoop.hdfs.server.common.StorageInfo;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AddBlockOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AddCacheDirectiveInfoOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AddCachePoolOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AddCloseOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AddOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AllocateBlockIdOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AllowSnapshotOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.AppendOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.CancelDelegationTokenOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.CloseOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.ConcatDeleteOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.CreateSnapshotOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.DeleteOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.DeleteSnapshotOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.DisallowSnapshotOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.GetDelegationTokenOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.LogSegmentOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.MkdirOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.ModifyCacheDirectiveInfoOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.ModifyCachePoolOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.OpInstanceCache;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.ReassignLeaseOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RemoveCacheDirectiveInfoOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RemoveCachePoolOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RemoveXAttrOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RenameOldOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RenameOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RenameSnapshotOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RenewDelegationTokenOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RollingUpgradeFinalizeOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RollingUpgradeOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.RollingUpgradeStartOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetAclOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetGenstampV1Op;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetGenstampV2Op;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetOwnerOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetPermissionsOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetQuotaOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetQuotaByStorageTypeOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetReplicationOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetStoragePolicyOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SetXAttrOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.SymlinkOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.TimesOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.TruncateOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.UpdateBlocksOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp.UpdateMasterKeyOp;
import org.apache.hadoop.hdfs.server.namenode.JournalSet.JournalAndStream;
import org.apache.hadoop.hdfs.server.namenode.metrics.NameNodeMetrics;
import org.apache.hadoop.hdfs.server.protocol.NamenodeRegistration;
import org.apache.hadoop.hdfs.server.protocol.NamespaceInfo;
import org.apache.hadoop.hdfs.server.protocol.RemoteEditLogManifest;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.ipc.Server;
import org.apache.hadoop.security.token.delegation.DelegationKey;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * FSEditLog maintains a log of the namespace modifications.
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class FSEditLog implements LogsPurgeable {

    public static final Log LOG = LogFactory.getLog(FSEditLog.class);

    /**
     * State machine for edit log.
     *
     * In a non-HA setup:
     *
     * The log starts in UNINITIALIZED state upon construction. Once it's
     * initialized, it is usually in IN_SEGMENT state, indicating that edits may
     * be written. In the middle of a roll, or while saving the namespace, it
     * briefly enters the BETWEEN_LOG_SEGMENTS state, indicating that the previous
     * segment has been closed, but the new one has not yet been opened.
     *
     * In an HA setup:
     *
     * The log starts in UNINITIALIZED state upon construction. Once it's
     * initialized, it sits in the OPEN_FOR_READING state the entire time that the
     * NN is in standby. Upon the NN transition to active, the log will be CLOSED,
     * and then move to being BETWEEN_LOG_SEGMENTS, much as if the NN had just
     * started up, and then will move to IN_SEGMENT so it can begin writing to the
     * log. The log states will then revert to behaving as they do in a non-HA
     * setup.
     */
    private enum State {

        UNINITIALIZED, BETWEEN_LOG_SEGMENTS, IN_SEGMENT, OPEN_FOR_READING, CLOSED
    }

    private State state = State.UNINITIALIZED;

    //initialize
    private JournalSet journalSet = null;

    private EditLogOutputStream editLogStream = null;

    // a monotonically increasing counter that represents transactionIds.
    // All of the threads which update/increment txid are synchronized,
    // so make txid volatile instead of AtomicLong.
    private volatile long txid = 0;

    // stores the last synced transactionId.
    private long synctxid = 0;

    // the first txid of the log that's currently open for writing.
    // If this value is N, we are currently writing to edits_inprogress_N
    private volatile long curSegmentTxId = HdfsServerConstants.INVALID_TXID;

    // the time of printing the statistics to the log file.
    private long lastPrintTime;

    // is a sync currently running?
    private volatile boolean isSyncRunning;

    // is an automatic sync scheduled?
    private volatile boolean isAutoSyncScheduled = false;

    // these are statistics counters.
    // number of transactions
    private long numTransactions;

    private final AtomicLong numTransactionsBatchedInSync = new AtomicLong();

    // total time for all transactions
    private long totalTimeTransactions;

    private NameNodeMetrics metrics;

    private final NNStorage storage;

    private final Configuration conf;

    private final List<URI> editsDirs;

    protected final OpInstanceCache cache = new OpInstanceCache();

    /**
     * The edit directories that are shared between primary and secondary.
     */
    private final List<URI> sharedEditsDirs;

    /**
     * Take this lock when adding journals to or closing the JournalSet. Allows
     * us to ensure that the JournalSet isn't closed or updated underneath us
     * in selectInputStreams().
     */
    private final Object journalSetLock = new Object();

    private static class TransactionId {

        public long txid;

        TransactionId(long value) {
            this.txid = value;
        }
    }

    // stores the most current transactionId of this thread.
    private static final ThreadLocal<TransactionId> myTransactionId = new ThreadLocal<TransactionId>() {

        @Override
        protected synchronized TransactionId initialValue() {
            // If an RPC call did not generate any transactions,
            // logSync() should exit without syncing
            // Therefore the initial value of myTransactionId should be 0
            return new TransactionId(0L);
        }
    };

    static FSEditLog internal$newInstance0(Configuration conf, NNStorage storage, List<URI> editsDirs) {
        boolean asyncEditLogging = conf.getBoolean(DFSConfigKeys.DFS_NAMENODE_EDITS_ASYNC_LOGGING, DFSConfigKeys.DFS_NAMENODE_EDITS_ASYNC_LOGGING_DEFAULT);
        LOG.info("Edit logging is async:" + asyncEditLogging);
        return asyncEditLogging ? new FSEditLogAsync(conf, storage, editsDirs) : new FSEditLog(conf, storage, editsDirs);
    }

    /**
     * Constructor for FSEditLog. Underlying journals are constructed, but
     * no streams are opened until open() is called.
     *
     * @param conf The namenode configuration
     * @param storage Storage object used by namenode
     * @param editsDirs List of journals to use
     */
    FSEditLog(Configuration conf, NNStorage storage, List<URI> editsDirs) {
        isSyncRunning = false;
        this.conf = conf;
        this.storage = storage;
        metrics = NameNode.getNameNodeMetrics();
        lastPrintTime = monotonicNow();
        // If this list is empty, an error will be thrown on first use
        // of the editlog, as no journals will exist
        this.editsDirs = Lists.newArrayList(editsDirs);
        this.sharedEditsDirs = FSNamesystem.getSharedEditsDirs(conf);
    }

    public synchronized void internal$initJournalsForWrite1() {
        Preconditions.checkState(state == State.UNINITIALIZED || state == State.CLOSED, "Unexpected state: %s", state);
        initJournals(this.editsDirs);
        state = State.BETWEEN_LOG_SEGMENTS;
    }

    public synchronized void initSharedJournalsForRead() {
        if (state == State.OPEN_FOR_READING) {
            LOG.warn("Initializing shared journals for READ, already open for READ", new Exception());
            return;
        }
        Preconditions.checkState(state == State.UNINITIALIZED || state == State.CLOSED);
        initJournals(this.sharedEditsDirs);
        state = State.OPEN_FOR_READING;
    }

    private synchronized void internal$initJournals2(List<URI> dirs) {
        int minimumRedundantJournals = conf.getInt(DFSConfigKeys.DFS_NAMENODE_EDITS_DIR_MINIMUM_KEY, DFSConfigKeys.DFS_NAMENODE_EDITS_DIR_MINIMUM_DEFAULT);
        synchronized (journalSetLock) {
            journalSet = new JournalSet(minimumRedundantJournals);
            for (URI u : dirs) {
                boolean required = FSNamesystem.getRequiredNamespaceEditsDirs(conf).contains(u);
                if (u.getScheme().equals(NNStorage.LOCAL_URI_SCHEME)) {
                    StorageDirectory sd = storage.getStorageDirectory(u);
                    if (sd != null) {
                        journalSet.add(new FileJournalManager(conf, sd, storage), required, sharedEditsDirs.contains(u));
                    }
                } else {
                    journalSet.add(createJournal(u), required, sharedEditsDirs.contains(u));
                }
            }
        }
        if (journalSet.isEmpty()) {
            LOG.error("No edits directories configured!");
        }
    }

    /**
     * Get the list of URIs the editlog is using for storage
     * @return collection of URIs in use by the edit log
     */
    Collection<URI> internal$getEditURIs3() {
        return editsDirs;
    }

    /**
     * Initialize the output stream for logging, opening the first
     * log segment.
     */
    synchronized void internal$openForWrite4(int layoutVersion) throws IOException {
        Preconditions.checkState(state == State.BETWEEN_LOG_SEGMENTS, "Bad state: %s", state);
        long segmentTxId = getLastWrittenTxId() + 1;
        // Safety check: we should never start a segment if there are
        // newer txids readable.
        List<EditLogInputStream> streams = new ArrayList<EditLogInputStream>();
        journalSet.selectInputStreams(streams, segmentTxId, true, false);
        if (!streams.isEmpty()) {
            String error = String.format("Cannot start writing at txid %s " + "when there is a stream available for read: %s", segmentTxId, streams.get(0));
            IOUtils.cleanup(LOG, streams.toArray(new EditLogInputStream[0]));
            throw new IllegalStateException(error);
        }
        startLogSegment(segmentTxId, true, layoutVersion);
        assert state == State.IN_SEGMENT : "Bad state: " + state;
    }

    /**
     * @return true if the log is currently open in write mode, regardless
     * of whether it actually has an open segment.
     */
    synchronized boolean internal$isOpenForWrite5() {
        return state == State.IN_SEGMENT || state == State.BETWEEN_LOG_SEGMENTS;
    }

    /**
     * Return true if the log is currently open in write mode.
     * This method is not synchronized and must be used only for metrics.
     * @return true if the log is currently open in write mode, regardless
     * of whether it actually has an open segment.
     */
    boolean isOpenForWriteWithoutLock() {
        return state == State.IN_SEGMENT || state == State.BETWEEN_LOG_SEGMENTS;
    }

    /**
     * @return true if the log is open in write mode and has a segment open
     * ready to take edits.
     */
    synchronized boolean internal$isSegmentOpen6() {
        return state == State.IN_SEGMENT;
    }

    /**
     * Return true the state is IN_SEGMENT.
     * This method is not synchronized and must be used only for metrics.
     * @return true if the log is open in write mode and has a segment open
     * ready to take edits.
     */
    boolean internal$isSegmentOpenWithoutLock7() {
        return state == State.IN_SEGMENT;
    }

    /**
     * @return true if the log is open in read mode.
     */
    public synchronized boolean isOpenForRead() {
        return state == State.OPEN_FOR_READING;
    }

    /**
     * Shutdown the file store.
     */
    synchronized void close() {
        if (state == State.CLOSED) {
            LOG.debug("Closing log when already closed");
            return;
        }
        try {
            if (state == State.IN_SEGMENT) {
                assert editLogStream != null;
                waitForSyncToFinish();
                endCurrentLogSegment(true);
            }
        } finally {
            if (journalSet != null && !journalSet.isEmpty()) {
                try {
                    synchronized (journalSetLock) {
                        journalSet.close();
                    }
                } catch (IOException ioe) {
                    LOG.warn("Error closing journalSet", ioe);
                }
            }
            state = State.CLOSED;
        }
    }

    /**
     * Format all configured journals which are not file-based.
     *
     * File-based journals are skipped, since they are formatted by the
     * Storage format code.
     */
    synchronized void formatNonFileJournals(NamespaceInfo nsInfo) throws IOException {
        Preconditions.checkState(state == State.BETWEEN_LOG_SEGMENTS, "Bad state: %s", state);
        for (JournalManager jm : journalSet.getJournalManagers()) {
            if (!(jm instanceof FileJournalManager)) {
                jm.format(nsInfo);
            }
        }
    }

    synchronized List<FormatConfirmable> getFormatConfirmables() {
        Preconditions.checkState(state == State.BETWEEN_LOG_SEGMENTS, "Bad state: %s", state);
        List<FormatConfirmable> ret = Lists.newArrayList();
        for (final JournalManager jm : journalSet.getJournalManagers()) {
            // The FJMs are confirmed separately since they are also
            // StorageDirectories
            if (!(jm instanceof FileJournalManager)) {
                ret.add(jm);
            }
        }
        return ret;
    }

    /**
     * Write an operation to the edit log.
     * <p/>
     * Additionally, this will sync the edit log if required by the underlying
     * edit stream's automatic sync policy (e.g. when the buffer is full, or
     * if a time interval has elapsed).
     */
    void logEdit(final FSEditLogOp op) {
        boolean needsSync = false;
        synchronized (this) {
            assert isOpenForWrite() : "bad state: " + state;
            // wait if an automatic sync is scheduled
            waitIfAutoSyncScheduled();
            beginTransaction(op);
            // check if it is time to schedule an automatic sync
            needsSync = doEditTransaction(op);
            if (needsSync) {
                isAutoSyncScheduled = true;
            }
        }
        // Sync the log if an automatic sync is required.
        if (needsSync) {
            logSync();
        }
    }

    synchronized boolean internal$doEditTransaction8(final FSEditLogOp op) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("doEditTx() op=" + op + " txid=" + txid);
        }
        assert op.hasTransactionId() : "Transaction id is not set for " + op + " EditLog.txId=" + txid;
        long start = monotonicNow();
        try {
            editLogStream.write(op);
        } catch (IOException ex) {
            // All journals failed, it is handled in logSync.
        } finally {
            op.reset();
        }
        endTransaction(start);
        return shouldForceSync();
    }

    /**
     * Wait if an automatic sync is scheduled
     */
    synchronized void waitIfAutoSyncScheduled() {
        try {
            while (isAutoSyncScheduled) {
                this.wait(1000);
            }
        } catch (InterruptedException e) {
        }
    }

    /**
     * Signal that an automatic sync scheduling is done if it is scheduled
     */
    synchronized void internal$doneWithAutoSyncScheduling9() {
        if (isAutoSyncScheduled) {
            isAutoSyncScheduled = false;
            notifyAll();
        }
    }

    /**
     * Check if should automatically sync buffered edits to
     * persistent store
     *
     * @return true if any of the edit stream says that it should sync
     */
    private boolean internal$shouldForceSync10() {
        return editLogStream.shouldForceSync();
    }

    protected void internal$beginTransaction11(final FSEditLogOp op) {
        assert Thread.holdsLock(this);
        // get a new transactionId
        txid++;
        //
        // record the transactionId when new data was written to the edits log
        //
        TransactionId id = myTransactionId.get();
        id.txid = txid;
        if (op != null) {
            op.setTransactionId(txid);
        }
    }

    private void internal$endTransaction12(long start) {
        assert Thread.holdsLock(this);
        // update statistics
        long end = monotonicNow();
        numTransactions++;
        totalTimeTransactions += (end - start);
        if (// Metrics is non-null only when used inside name node
        metrics != null)
            metrics.addTransaction(end - start);
    }

    /**
     * Return the transaction ID of the last transaction written to the log.
     */
    public synchronized long internal$getLastWrittenTxId13() {
        return txid;
    }

    /**
     * Return the transaction ID of the last transaction written to the log.
     * This method is not synchronized and must be used only for metrics.
     * @return The transaction ID of the last transaction written to the log
     */
    long internal$getLastWrittenTxIdWithoutLock14() {
        return txid;
    }

    /**
     * @return the first transaction ID in the current log segment
     */
    @VisibleForTesting
    public synchronized long internal$getCurSegmentTxId15() {
        Preconditions.checkState(isSegmentOpen(), "Bad state: %s", state);
        return curSegmentTxId;
    }

    /**
     * Return the first transaction ID in the current log segment.
     * This method is not synchronized and must be used only for metrics.
     * @return The first transaction ID in the current log segment
     */
    long internal$getCurSegmentTxIdWithoutLock16() {
        return curSegmentTxId;
    }

    /**
     * Set the transaction ID to use for the next transaction written.
     */
    synchronized void internal$setNextTxId17(long nextTxId) {
        Preconditions.checkArgument(synctxid <= txid && nextTxId >= txid, "May not decrease txid." + " synctxid=%s txid=%s nextTxId=%s", synctxid, txid, nextTxId);
        txid = nextTxId - 1;
    }

    /**
     * Blocks until all ongoing edits have been synced to disk.
     * This differs from logSync in that it waits for edits that have been
     * written by other threads, not just edits from the calling thread.
     *
     * NOTE: this should be done while holding the FSNamesystem lock, or
     * else more operations can start writing while this is in progress.
     */
    void logSyncAll() {
        // Make sure we're synced up to the most recent transaction ID.
        long lastWrittenTxId = getLastWrittenTxId();
        LOG.info("logSyncAll toSyncToTxId=" + lastWrittenTxId + " lastSyncedTxid=" + synctxid + " mostRecentTxid=" + txid);
        logSync(lastWrittenTxId);
        lastWrittenTxId = getLastWrittenTxId();
        LOG.info("Done logSyncAll lastWrittenTxId=" + lastWrittenTxId + " lastSyncedTxid=" + synctxid + " mostRecentTxid=" + txid);
    }

    /**
     * Sync all modifications done by this thread.
     *
     * The internal concurrency design of this class is as follows:
     *   - Log items are written synchronized into an in-memory buffer,
     *     and each assigned a transaction ID.
     *   - When a thread (client) would like to sync all of its edits, logSync()
     *     uses a ThreadLocal transaction ID to determine what edit number must
     *     be synced to.
     *   - The isSyncRunning volatile boolean tracks whether a sync is currently
     *     under progress.
     *
     * The data is double-buffered within each edit log implementation so that
     * in-memory writing can occur in parallel with the on-disk writing.
     *
     * Each sync occurs in three steps:
     *   1. synchronized, it swaps the double buffer and sets the isSyncRunning
     *      flag.
     *   2. unsynchronized, it flushes the data to storage
     *   3. synchronized, it resets the flag and notifies anyone waiting on the
     *      sync.
     *
     * The lack of synchronization on step 2 allows other threads to continue
     * to write into the memory buffer while the sync is in progress.
     * Because this step is unsynchronized, actions that need to avoid
     * concurrency with sync() should be synchronized and also call
     * waitForSyncToFinish() before assuming they are running alone.
     */
    public void logSync() {
        // Fetch the transactionId of this thread.
        logSync(myTransactionId.get().txid);
    }

    protected void internal$logSync18(long mytxid) {
        long lastJournalledTxId = HdfsServerConstants.INVALID_TXID;
        boolean sync = false;
        long editsBatchedInSync = 0;
        try {
            EditLogOutputStream logStream = null;
            synchronized (this) {
                try {
                    printStatistics(false);
                    // if somebody is already syncing, then wait
                    while (mytxid > synctxid && isSyncRunning) {
                        try {
                            wait(1000);
                        } catch (InterruptedException ie) {
                        }
                    }
                    //
                    // If this transaction was already flushed, then nothing to do
                    //
                    if (mytxid <= synctxid) {
                        return;
                    }
                    // now, this thread will do the sync.  track if other edits were
                    // included in the sync - ie. batched.  if this is the only edit
                    // synced then the batched count is 0
                    lastJournalledTxId = editLogStream.getLastJournalledTxId();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("logSync(tx) synctxid=" + " lastJournalledTxId=" + lastJournalledTxId + " mytxid=" + mytxid);
                    }
                    assert lastJournalledTxId <= txid : "lastJournalledTxId exceeds txid";
                    // The stream has already been flushed, or there are no active streams
                    // We still try to flush up to mytxid
                    if (lastJournalledTxId <= synctxid) {
                        lastJournalledTxId = mytxid;
                    }
                    editsBatchedInSync = lastJournalledTxId - synctxid - 1;
                    isSyncRunning = true;
                    sync = true;
                    // swap buffers
                    try {
                        if (journalSet.isEmpty()) {
                            throw new IOException("No journals available to flush");
                        }
                        editLogStream.setReadyToFlush();
                    } catch (IOException e) {
                        final String msg = "Could not sync enough journals to persistent storage " + "due to " + e.getMessage() + ". " + "Unsynced transactions: " + (txid - synctxid);
                        LOG.fatal(msg, new Exception());
                        synchronized (journalSetLock) {
                            IOUtils.cleanup(LOG, journalSet);
                        }
                        terminate(1, msg);
                    }
                } finally {
                    // Prevent RuntimeException from blocking other log edit write
                    doneWithAutoSyncScheduling();
                }
                //editLogStream may become null,
                //so store a local variable for flush.
                logStream = editLogStream;
            }
            // do the sync
            long start = monotonicNow();
            try {
                if (logStream != null) {
                    logStream.flush();
                }
            } catch (IOException ex) {
                synchronized (this) {
                    final String msg = "Could not sync enough journals to persistent storage. " + "Unsynced transactions: " + (txid - synctxid);
                    LOG.fatal(msg, new Exception());
                    synchronized (journalSetLock) {
                        IOUtils.cleanup(LOG, journalSet);
                    }
                    terminate(1, msg);
                }
            }
            long elapsed = monotonicNow() - start;
            if (metrics != null) {
                // Metrics non-null only when used inside name node
                metrics.addSync(elapsed);
                metrics.incrTransactionsBatchedInSync(editsBatchedInSync);
                numTransactionsBatchedInSync.addAndGet(editsBatchedInSync);
            }
        } finally {
            // Prevent RuntimeException from blocking other log edit sync
            synchronized (this) {
                if (sync) {
                    synctxid = lastJournalledTxId;
                    for (JournalManager jm : journalSet.getJournalManagers()) {
                        /**
                         * {@link FileJournalManager#lastReadableTxId} is only meaningful
                         * for file-based journals. Therefore the interface is not added to
                         * other types of {@link JournalManager}.
                         */
                        if (jm instanceof FileJournalManager) {
                            ((FileJournalManager) jm).setLastReadableTxId(synctxid);
                        }
                    }
                    isSyncRunning = false;
                }
                this.notifyAll();
            }
        }
    }

    //
    // print statistics every 1 minute.
    //
    private void internal$printStatistics19(boolean force) {
        long now = monotonicNow();
        if (lastPrintTime + 60000 > now && !force) {
            return;
        }
        lastPrintTime = now;
        StringBuilder buf = new StringBuilder();
        buf.append("Number of transactions: ");
        buf.append(numTransactions);
        buf.append(" Total time for transactions(ms): ");
        buf.append(totalTimeTransactions);
        buf.append(" Number of transactions batched in Syncs: ");
        buf.append(numTransactionsBatchedInSync.get());
        buf.append(" Number of syncs: ");
        buf.append(editLogStream.getNumSync());
        buf.append(" SyncTimes(ms): ");
        buf.append(journalSet.getSyncTimes());
        LOG.info(buf);
    }

    /**
     * Record the RPC IDs if necessary
     */
    private void logRpcIds(FSEditLogOp op, boolean toLogRpcIds) {
        if (toLogRpcIds) {
            op.setRpcClientId(Server.getClientId());
            op.setRpcCallId(Server.getCallId());
        }
    }

    public void logAppendFile(String path, INodeFile file, boolean newBlock, boolean toLogRpcIds) {
        FileUnderConstructionFeature uc = file.getFileUnderConstructionFeature();
        assert uc != null;
        AppendOp op = AppendOp.getInstance(cache.get()).setPath(path).setClientName(uc.getClientName()).setClientMachine(uc.getClientMachine()).setNewBlock(newBlock);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add open lease record to edit log.
     * Records the block locations of the last block.
     */
    public void logOpenFile(String path, INodeFile newNode, boolean overwrite, boolean toLogRpcIds) {
        Preconditions.checkArgument(newNode.isUnderConstruction());
        PermissionStatus permissions = newNode.getPermissionStatus();
        AddOp op = AddOp.getInstance(cache.get()).setInodeId(newNode.getId()).setPath(path).setReplication(newNode.getFileReplication()).setModificationTime(newNode.getModificationTime()).setAccessTime(newNode.getAccessTime()).setBlockSize(newNode.getPreferredBlockSize()).setBlocks(newNode.getBlocks()).setPermissionStatus(permissions).setClientName(newNode.getFileUnderConstructionFeature().getClientName()).setClientMachine(newNode.getFileUnderConstructionFeature().getClientMachine()).setOverwrite(overwrite).setStoragePolicyId(newNode.getLocalStoragePolicyID());
        AclFeature f = newNode.getAclFeature();
        if (f != null) {
            op.setAclEntries(AclStorage.readINodeLogicalAcl(newNode));
        }
        XAttrFeature x = newNode.getXAttrFeature();
        if (x != null) {
            op.setXAttrs(x.getXAttrs());
        }
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add close lease record to edit log.
     */
    public void logCloseFile(String path, INodeFile newNode) {
        CloseOp op = CloseOp.getInstance(cache.get()).setPath(path).setReplication(newNode.getFileReplication()).setModificationTime(newNode.getModificationTime()).setAccessTime(newNode.getAccessTime()).setBlockSize(newNode.getPreferredBlockSize()).setBlocks(newNode.getBlocks()).setPermissionStatus(newNode.getPermissionStatus());
        logEdit(op);
    }

    public void logAddBlock(String path, INodeFile file) {
        Preconditions.checkArgument(file.isUnderConstruction());
        BlockInfo[] blocks = file.getBlocks();
        Preconditions.checkState(blocks != null && blocks.length > 0);
        BlockInfo pBlock = blocks.length > 1 ? blocks[blocks.length - 2] : null;
        BlockInfo lastBlock = blocks[blocks.length - 1];
        AddBlockOp op = AddBlockOp.getInstance(cache.get()).setPath(path).setPenultimateBlock(pBlock).setLastBlock(lastBlock);
        logEdit(op);
    }

    public void logUpdateBlocks(String path, INodeFile file, boolean toLogRpcIds) {
        Preconditions.checkArgument(file.isUnderConstruction());
        UpdateBlocksOp op = UpdateBlocksOp.getInstance(cache.get()).setPath(path).setBlocks(file.getBlocks());
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add create directory record to edit log
     */
    public void logMkDir(String path, INode newNode) {
        PermissionStatus permissions = newNode.getPermissionStatus();
        MkdirOp op = MkdirOp.getInstance(cache.get()).setInodeId(newNode.getId()).setPath(path).setTimestamp(newNode.getModificationTime()).setPermissionStatus(permissions);
        AclFeature f = newNode.getAclFeature();
        if (f != null) {
            op.setAclEntries(AclStorage.readINodeLogicalAcl(newNode));
        }
        XAttrFeature x = newNode.getXAttrFeature();
        if (x != null) {
            op.setXAttrs(x.getXAttrs());
        }
        logEdit(op);
    }

    /**
     * Add rename record to edit log.
     *
     * The destination should be the file name, not the destination directory.
     * TODO: use String parameters until just before writing to disk
     */
    void logRename(String src, String dst, long timestamp, boolean toLogRpcIds) {
        RenameOldOp op = RenameOldOp.getInstance(cache.get()).setSource(src).setDestination(dst).setTimestamp(timestamp);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add rename record to edit log.
     *
     * The destination should be the file name, not the destination directory.
     */
    void logRename(String src, String dst, long timestamp, boolean toLogRpcIds, Options.Rename... options) {
        RenameOp op = RenameOp.getInstance(cache.get()).setSource(src).setDestination(dst).setTimestamp(timestamp).setOptions(options);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add set replication record to edit log
     */
    void logSetReplication(String src, short replication) {
        SetReplicationOp op = SetReplicationOp.getInstance(cache.get()).setPath(src).setReplication(replication);
        logEdit(op);
    }

    /**
     * Add set storage policy id record to edit log
     */
    void logSetStoragePolicy(String src, byte policyId) {
        SetStoragePolicyOp op = SetStoragePolicyOp.getInstance(cache.get()).setPath(src).setPolicyId(policyId);
        logEdit(op);
    }

    /**
     * Add set namespace quota record to edit log
     *
     * @param src the string representation of the path to a directory
     * @param nsQuota namespace quota
     * @param dsQuota diskspace quota
     */
    void logSetQuota(String src, long nsQuota, long dsQuota) {
        SetQuotaOp op = SetQuotaOp.getInstance(cache.get()).setSource(src).setNSQuota(nsQuota).setDSQuota(dsQuota);
        logEdit(op);
    }

    /**
     * Add set quota by storage type record to edit log
     */
    void logSetQuotaByStorageType(String src, long dsQuota, StorageType type) {
        SetQuotaByStorageTypeOp op = SetQuotaByStorageTypeOp.getInstance(cache.get()).setSource(src).setQuotaByStorageType(dsQuota, type);
        logEdit(op);
    }

    /**
     *  Add set permissions record to edit log
     */
    void logSetPermissions(String src, FsPermission permissions) {
        SetPermissionsOp op = SetPermissionsOp.getInstance(cache.get()).setSource(src).setPermissions(permissions);
        logEdit(op);
    }

    /**
     *  Add set owner record to edit log
     */
    void logSetOwner(String src, String username, String groupname) {
        SetOwnerOp op = SetOwnerOp.getInstance(cache.get()).setSource(src).setUser(username).setGroup(groupname);
        logEdit(op);
    }

    /**
     * concat(trg,src..) log
     */
    void logConcat(String trg, String[] srcs, long timestamp, boolean toLogRpcIds) {
        ConcatDeleteOp op = ConcatDeleteOp.getInstance(cache.get()).setTarget(trg).setSources(srcs).setTimestamp(timestamp);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add delete file record to edit log
     */
    void logDelete(String src, long timestamp, boolean toLogRpcIds) {
        DeleteOp op = DeleteOp.getInstance(cache.get()).setPath(src).setTimestamp(timestamp);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Add truncate file record to edit log
     */
    void logTruncate(String src, String clientName, String clientMachine, long size, long timestamp, Block truncateBlock) {
        TruncateOp op = TruncateOp.getInstance(cache.get()).setPath(src).setClientName(clientName).setClientMachine(clientMachine).setNewLength(size).setTimestamp(timestamp).setTruncateBlock(truncateBlock);
        logEdit(op);
    }

    /**
     * Add legacy block generation stamp record to edit log
     */
    void logLegacyGenerationStamp(long genstamp) {
        SetGenstampV1Op op = SetGenstampV1Op.getInstance(cache.get()).setGenerationStamp(genstamp);
        logEdit(op);
    }

    /**
     * Add generation stamp record to edit log
     */
    void logGenerationStamp(long genstamp) {
        SetGenstampV2Op op = SetGenstampV2Op.getInstance(cache.get()).setGenerationStamp(genstamp);
        logEdit(op);
    }

    /**
     * Record a newly allocated block ID in the edit log
     */
    void logAllocateBlockId(long blockId) {
        AllocateBlockIdOp op = AllocateBlockIdOp.getInstance(cache.get()).setBlockId(blockId);
        logEdit(op);
    }

    /**
     * Add access time record to edit log
     */
    void logTimes(String src, long mtime, long atime) {
        TimesOp op = TimesOp.getInstance(cache.get()).setPath(src).setModificationTime(mtime).setAccessTime(atime);
        logEdit(op);
    }

    /**
     * Add a create symlink record.
     */
    void logSymlink(String path, String value, long mtime, long atime, INodeSymlink node, boolean toLogRpcIds) {
        SymlinkOp op = SymlinkOp.getInstance(cache.get()).setId(node.getId()).setPath(path).setValue(value).setModificationTime(mtime).setAccessTime(atime).setPermissionStatus(node.getPermissionStatus());
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * log delegation token to edit log
     * @param id DelegationTokenIdentifier
     * @param expiryTime of the token
     */
    void logGetDelegationToken(DelegationTokenIdentifier id, long expiryTime) {
        GetDelegationTokenOp op = GetDelegationTokenOp.getInstance(cache.get()).setDelegationTokenIdentifier(id).setExpiryTime(expiryTime);
        logEdit(op);
    }

    void logRenewDelegationToken(DelegationTokenIdentifier id, long expiryTime) {
        RenewDelegationTokenOp op = RenewDelegationTokenOp.getInstance(cache.get()).setDelegationTokenIdentifier(id).setExpiryTime(expiryTime);
        logEdit(op);
    }

    void logCancelDelegationToken(DelegationTokenIdentifier id) {
        CancelDelegationTokenOp op = CancelDelegationTokenOp.getInstance(cache.get()).setDelegationTokenIdentifier(id);
        logEdit(op);
    }

    void logUpdateMasterKey(DelegationKey key) {
        UpdateMasterKeyOp op = UpdateMasterKeyOp.getInstance(cache.get()).setDelegationKey(key);
        logEdit(op);
    }

    void logReassignLease(String leaseHolder, String src, String newHolder) {
        ReassignLeaseOp op = ReassignLeaseOp.getInstance(cache.get()).setLeaseHolder(leaseHolder).setPath(src).setNewHolder(newHolder);
        logEdit(op);
    }

    void logCreateSnapshot(String snapRoot, String snapName, boolean toLogRpcIds) {
        CreateSnapshotOp op = CreateSnapshotOp.getInstance(cache.get()).setSnapshotRoot(snapRoot).setSnapshotName(snapName);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logDeleteSnapshot(String snapRoot, String snapName, boolean toLogRpcIds) {
        DeleteSnapshotOp op = DeleteSnapshotOp.getInstance(cache.get()).setSnapshotRoot(snapRoot).setSnapshotName(snapName);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logRenameSnapshot(String path, String snapOldName, String snapNewName, boolean toLogRpcIds) {
        RenameSnapshotOp op = RenameSnapshotOp.getInstance(cache.get()).setSnapshotRoot(path).setSnapshotOldName(snapOldName).setSnapshotNewName(snapNewName);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logAllowSnapshot(String path) {
        AllowSnapshotOp op = AllowSnapshotOp.getInstance(cache.get()).setSnapshotRoot(path);
        logEdit(op);
    }

    void logDisallowSnapshot(String path) {
        DisallowSnapshotOp op = DisallowSnapshotOp.getInstance(cache.get()).setSnapshotRoot(path);
        logEdit(op);
    }

    /**
     * Log a CacheDirectiveInfo returned from
     * {@link CacheManager#addDirective(CacheDirectiveInfo, FSPermissionChecker)}
     */
    void logAddCacheDirectiveInfo(CacheDirectiveInfo directive, boolean toLogRpcIds) {
        AddCacheDirectiveInfoOp op = AddCacheDirectiveInfoOp.getInstance(cache.get()).setDirective(directive);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logModifyCacheDirectiveInfo(CacheDirectiveInfo directive, boolean toLogRpcIds) {
        ModifyCacheDirectiveInfoOp op = ModifyCacheDirectiveInfoOp.getInstance(cache.get()).setDirective(directive);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logRemoveCacheDirectiveInfo(Long id, boolean toLogRpcIds) {
        RemoveCacheDirectiveInfoOp op = RemoveCacheDirectiveInfoOp.getInstance(cache.get()).setId(id);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logAddCachePool(CachePoolInfo pool, boolean toLogRpcIds) {
        AddCachePoolOp op = AddCachePoolOp.getInstance(cache.get()).setPool(pool);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logModifyCachePool(CachePoolInfo info, boolean toLogRpcIds) {
        ModifyCachePoolOp op = ModifyCachePoolOp.getInstance(cache.get()).setInfo(info);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logRemoveCachePool(String poolName, boolean toLogRpcIds) {
        RemoveCachePoolOp op = RemoveCachePoolOp.getInstance(cache.get()).setPoolName(poolName);
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logStartRollingUpgrade(long startTime) {
        RollingUpgradeStartOp op = RollingUpgradeStartOp.getInstance(cache.get());
        op.setTime(startTime);
        logEdit(op);
    }

    void logFinalizeRollingUpgrade(long finalizeTime) {
        RollingUpgradeOp op = RollingUpgradeFinalizeOp.getInstance(cache.get());
        op.setTime(finalizeTime);
        logEdit(op);
    }

    void logSetAcl(String src, List<AclEntry> entries) {
        final SetAclOp op = SetAclOp.getInstance(cache.get());
        op.src = src;
        op.aclEntries = entries;
        logEdit(op);
    }

    void logSetXAttrs(String src, List<XAttr> xAttrs, boolean toLogRpcIds) {
        final SetXAttrOp op = SetXAttrOp.getInstance(cache.get());
        op.src = src;
        op.xAttrs = xAttrs;
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    void logRemoveXAttrs(String src, List<XAttr> xAttrs, boolean toLogRpcIds) {
        final RemoveXAttrOp op = RemoveXAttrOp.getInstance(cache.get());
        op.src = src;
        op.xAttrs = xAttrs;
        logRpcIds(op, toLogRpcIds);
        logEdit(op);
    }

    /**
     * Get all the journals this edit log is currently operating on.
     */
    List<JournalAndStream> getJournals() {
        // The list implementation is CopyOnWriteArrayList,
        // so we don't need to synchronize this method.
        return journalSet.getAllJournalStreams();
    }

    /**
     * Used only by tests.
     */
    @VisibleForTesting
    public JournalSet internal$getJournalSet20() {
        return journalSet;
    }

    @VisibleForTesting
    synchronized void setJournalSetForTesting(JournalSet js) {
        this.journalSet = js;
    }

    /**
     * Used only by tests.
     */
    @VisibleForTesting
    void setMetricsForTests(NameNodeMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Return a manifest of what finalized edit logs are available
     */
    public synchronized RemoteEditLogManifest getEditLogManifest(long fromTxId) throws IOException {
        return journalSet.getEditLogManifest(fromTxId);
    }

    /**
     * Finalizes the current edit log and opens a new log segment.
     *
     * @param layoutVersion The layout version of the new edit log segment.
     * @return the transaction id of the BEGIN_LOG_SEGMENT transaction in the new
     * log.
     */
    synchronized long rollEditLog(int layoutVersion) throws IOException {
        LOG.info("Rolling edit logs");
        endCurrentLogSegment(true);
        long nextTxId = getLastWrittenTxId() + 1;
        startLogSegment(nextTxId, true, layoutVersion);
        assert curSegmentTxId == nextTxId;
        return nextTxId;
    }

    /**
     * Start writing to the log segment with the given txid.
     * Transitions from BETWEEN_LOG_SEGMENTS state to IN_LOG_SEGMENT state.
     */
    synchronized void internal$startLogSegment21(final long segmentTxId, boolean writeHeaderTxn, int layoutVersion) throws IOException {
        LOG.info("Starting log segment at " + segmentTxId);
        Preconditions.checkArgument(segmentTxId > 0, "Bad txid: %s", segmentTxId);
        Preconditions.checkState(state == State.BETWEEN_LOG_SEGMENTS, "Bad state: %s", state);
        Preconditions.checkState(segmentTxId > curSegmentTxId, "Cannot start writing to log segment " + segmentTxId + " when previous log segment started at " + curSegmentTxId);
        Preconditions.checkArgument(segmentTxId == txid + 1, "Cannot start log segment at txid %s when next expected " + "txid is %s", segmentTxId, txid + 1);
        numTransactions = 0;
        totalTimeTransactions = 0;
        numTransactionsBatchedInSync.set(0L);
        // TODO no need to link this back to storage anymore!
        // See HDFS-2174.
        storage.attemptRestoreRemovedStorage();
        try {
            editLogStream = journalSet.startLogSegment(segmentTxId, layoutVersion);
        } catch (IOException ex) {
            throw new IOException("Unable to start log segment " + segmentTxId + ": too few journals successfully started.", ex);
        }
        curSegmentTxId = segmentTxId;
        state = State.IN_SEGMENT;
        if (writeHeaderTxn) {
            logEdit(LogSegmentOp.getInstance(cache.get(), FSEditLogOpCodes.OP_START_LOG_SEGMENT));
            logSync();
        }
    }

    /**
     * Finalize the current log segment.
     * Transitions from IN_SEGMENT state to BETWEEN_LOG_SEGMENTS state.
     */
    public synchronized void endCurrentLogSegment(boolean writeEndTxn) {
        LOG.info("Ending log segment " + curSegmentTxId + ", " + getLastWrittenTxId());
        Preconditions.checkState(isSegmentOpen(), "Bad state: %s", state);
        if (writeEndTxn) {
            logEdit(LogSegmentOp.getInstance(cache.get(), FSEditLogOpCodes.OP_END_LOG_SEGMENT));
        }
        // always sync to ensure all edits are flushed.
        logSyncAll();
        printStatistics(true);
        final long lastTxId = getLastWrittenTxId();
        final long lastSyncedTxId = getSyncTxId();
        Preconditions.checkArgument(lastTxId == lastSyncedTxId, "LastWrittenTxId %s is expected to be the same as lastSyncedTxId %s", lastTxId, lastSyncedTxId);
        try {
            journalSet.finalizeLogSegment(curSegmentTxId, lastTxId);
            editLogStream = null;
        } catch (IOException e) {
            //All journals have failed, it will be handled in logSync.
        }
        state = State.BETWEEN_LOG_SEGMENTS;
    }

    /**
     * Abort all current logs. Called from the backup node.
     */
    synchronized void abortCurrentLogSegment() {
        try {
            //Check for null, as abort can be called any time.
            if (editLogStream != null) {
                editLogStream.abort();
                editLogStream = null;
                state = State.BETWEEN_LOG_SEGMENTS;
            }
        } catch (IOException e) {
            LOG.warn("All journals failed to abort", e);
        }
    }

    /**
     * Archive any log files that are older than the given txid.
     *
     * If the edit log is not open for write, then this call returns with no
     * effect.
     */
    @Override
    public synchronized void purgeLogsOlderThan(final long minTxIdToKeep) {
        // Should not purge logs unless they are open for write.
        // This prevents the SBN from purging logs on shared storage, for example.
        if (!isOpenForWrite()) {
            return;
        }
        assert // on format this is no-op
        curSegmentTxId == HdfsServerConstants.INVALID_TXID || minTxIdToKeep <= curSegmentTxId : "cannot purge logs older than txid " + minTxIdToKeep + " when current segment starts at " + curSegmentTxId;
        if (minTxIdToKeep == 0) {
            return;
        }
        // This could be improved to not need synchronization. But currently,
        // journalSet is not threadsafe, so we need to synchronize this method.
        try {
            journalSet.purgeLogsOlderThan(minTxIdToKeep);
        } catch (IOException ex) {
            //All journals have failed, it will be handled in logSync.
        }
    }

    /**
     * The actual sync activity happens while not synchronized on this object.
     * Thus, synchronized activities that require that they are not concurrent
     * with file operations should wait for any running sync to finish.
     */
    synchronized void waitForSyncToFinish() {
        while (isSyncRunning) {
            try {
                wait(1000);
            } catch (InterruptedException ie) {
            }
        }
    }

    /**
     * Return the txid of the last synced transaction.
     */
    public synchronized long getSyncTxId() {
        return synctxid;
    }

    // sets the initial capacity of the flush buffer.
    synchronized void setOutputBufferCapacity(int size) {
        journalSet.setOutputBufferCapacity(size);
    }

    /**
     * Create (or find if already exists) an edit output stream, which
     * streams journal records (edits) to the specified backup node.<br>
     *
     * The new BackupNode will start receiving edits the next time this
     * NameNode's logs roll.
     *
     * @param bnReg the backup node registration information.
     * @param nnReg this (active) name-node registration.
     * @throws IOException
     */
    synchronized void registerBackupNode(// backup node
    NamenodeRegistration bnReg, // active name-node
    NamenodeRegistration nnReg) throws IOException {
        if (bnReg.isRole(NamenodeRole.CHECKPOINT))
            // checkpoint node does not stream edits
            return;
        JournalManager jas = findBackupJournal(bnReg);
        if (jas != null) {
            // already registered
            LOG.info("Backup node " + bnReg + " re-registers");
            return;
        }
        LOG.info("Registering new backup node: " + bnReg);
        BackupJournalManager bjm = new BackupJournalManager(bnReg, nnReg);
        synchronized (journalSetLock) {
            journalSet.add(bjm, false);
        }
    }

    synchronized void releaseBackupStream(NamenodeRegistration registration) throws IOException {
        BackupJournalManager bjm = this.findBackupJournal(registration);
        if (bjm != null) {
            LOG.info("Removing backup journal " + bjm);
            synchronized (journalSetLock) {
                journalSet.remove(bjm);
            }
        }
    }

    /**
     * Find the JournalAndStream associated with this BackupNode.
     *
     * @return null if it cannot be found
     */
    private synchronized BackupJournalManager findBackupJournal(NamenodeRegistration bnReg) {
        for (JournalManager bjm : journalSet.getJournalManagers()) {
            if ((bjm instanceof BackupJournalManager) && ((BackupJournalManager) bjm).matchesRegistration(bnReg)) {
                return (BackupJournalManager) bjm;
            }
        }
        return null;
    }

    /**
     * Write an operation to the edit log. Do not sync to persistent
     * store yet.
     */
    synchronized void logEdit(final int length, final byte[] data) {
        beginTransaction(null);
        long start = monotonicNow();
        try {
            editLogStream.writeRaw(data, 0, length);
        } catch (IOException ex) {
            // All journals have failed, it will be handled in logSync.
        }
        endTransaction(start);
    }

    /**
     * Run recovery on all journals to recover any unclosed segments
     */
    synchronized void internal$recoverUnclosedStreams22() {
        Preconditions.checkState(state == State.BETWEEN_LOG_SEGMENTS, "May not recover segments - wrong state: %s", state);
        try {
            journalSet.recoverUnfinalizedSegments();
        } catch (IOException ex) {
            // All journals have failed, it is handled in logSync.
            // TODO: are we sure this is OK?
        }
    }

    public long getSharedLogCTime() throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            if (jas.isShared()) {
                return jas.getManager().getJournalCTime();
            }
        }
        throw new IOException("No shared log found.");
    }

    public synchronized void doPreUpgradeOfSharedLog() throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            if (jas.isShared()) {
                jas.getManager().doPreUpgrade();
            }
        }
    }

    public synchronized void doUpgradeOfSharedLog() throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            if (jas.isShared()) {
                jas.getManager().doUpgrade(storage);
            }
        }
    }

    public synchronized void doFinalizeOfSharedLog() throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            if (jas.isShared()) {
                jas.getManager().doFinalize();
            }
        }
    }

    public synchronized boolean canRollBackSharedLog(StorageInfo prevStorage, int targetLayoutVersion) throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            if (jas.isShared()) {
                return jas.getManager().canRollBack(storage, prevStorage, targetLayoutVersion);
            }
        }
        throw new IOException("No shared log found.");
    }

    public synchronized void doRollback() throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            if (jas.isShared()) {
                jas.getManager().doRollback();
            }
        }
    }

    public synchronized void discardSegments(long markerTxid) throws IOException {
        for (JournalAndStream jas : journalSet.getAllJournalStreams()) {
            jas.getManager().discardSegments(markerTxid);
        }
    }

    public void internal$selectInputStreams23(Collection<EditLogInputStream> streams, long fromTxId, boolean inProgressOk, boolean onlyDurableTxns) throws IOException {
        journalSet.selectInputStreams(streams, fromTxId, inProgressOk, onlyDurableTxns);
    }

    public Collection<EditLogInputStream> selectInputStreams(long fromTxId, long toAtLeastTxId) throws IOException {
        return selectInputStreams(fromTxId, toAtLeastTxId, null, true, false);
    }

    public Collection<EditLogInputStream> internal$selectInputStreams24(long fromTxId, long toAtLeastTxId, MetaRecoveryContext recovery, boolean inProgressOK) throws IOException {
        return selectInputStreams(fromTxId, toAtLeastTxId, recovery, inProgressOK, false);
    }

    /**
     * Select a list of input streams.
     *
     * @param fromTxId first transaction in the selected streams
     * @param toAtLeastTxId the selected streams must contain this transaction
     * @param recovery recovery context
     * @param inProgressOk set to true if in-progress streams are OK
     * @param onlyDurableTxns set to true if streams are bounded
     *                        by the durable TxId
     */
    public Collection<EditLogInputStream> internal$selectInputStreams25(long fromTxId, long toAtLeastTxId, MetaRecoveryContext recovery, boolean inProgressOk, boolean onlyDurableTxns) throws IOException {
        List<EditLogInputStream> streams = new ArrayList<EditLogInputStream>();
        synchronized (journalSetLock) {
            Preconditions.checkState(journalSet.isOpen(), "Cannot call " + "selectInputStreams() on closed FSEditLog");
            selectInputStreams(streams, fromTxId, inProgressOk, onlyDurableTxns);
        }
        try {
            checkForGaps(streams, fromTxId, toAtLeastTxId, inProgressOk);
        } catch (IOException e) {
            if (recovery != null) {
                // If recovery mode is enabled, continue loading even if we know we
                // can't load up to toAtLeastTxId.
                LOG.error(e);
            } else {
                closeAllStreams(streams);
                throw e;
            }
        }
        return streams;
    }

    /**
     * Check for gaps in the edit log input stream list.
     * Note: we're assuming that the list is sorted and that txid ranges don't
     * overlap.  This could be done better and with more generality with an
     * interval tree.
     */
    private void internal$checkForGaps26(List<EditLogInputStream> streams, long fromTxId, long toAtLeastTxId, boolean inProgressOk) throws IOException {
        Iterator<EditLogInputStream> iter = streams.iterator();
        long txId = fromTxId;
        while (true) {
            if (txId > toAtLeastTxId)
                return;
            if (!iter.hasNext())
                break;
            EditLogInputStream elis = iter.next();
            if (elis.getFirstTxId() > txId)
                break;
            long next = elis.getLastTxId();
            if (next == HdfsServerConstants.INVALID_TXID) {
                if (!inProgressOk) {
                    throw new RuntimeException("inProgressOk = false, but " + "selectInputStreams returned an in-progress edit " + "log input stream (" + elis + ")");
                }
                // We don't know where the in-progress stream ends.
                // It could certainly go all the way up to toAtLeastTxId.
                return;
            }
            txId = next + 1;
        }
        throw new IOException(String.format("Gap in transactions. Expected to " + "be able to read up until at least txid %d but unable to find any " + "edit logs containing txid %d", toAtLeastTxId, txId));
    }

    /**
     * Close all the streams in a collection
     * @param streams The list of streams to close
     */
    static void closeAllStreams(Iterable<EditLogInputStream> streams) {
        for (EditLogInputStream s : streams) {
            IOUtils.closeStream(s);
        }
    }

    /**
     * Retrieve the implementation class for a Journal scheme.
     * @param conf The configuration to retrieve the information from
     * @param uriScheme The uri scheme to look up.
     * @return the class of the journal implementation
     * @throws IllegalArgumentException if no class is configured for uri
     */
    static Class<? extends JournalManager> getJournalClass(Configuration conf, String uriScheme) {
        String key = DFSConfigKeys.DFS_NAMENODE_EDITS_PLUGIN_PREFIX + "." + uriScheme;
        Class<? extends JournalManager> clazz = null;
        try {
            clazz = conf.getClass(key, null, JournalManager.class);
        } catch (RuntimeException re) {
            throw new IllegalArgumentException("Invalid class specified for " + uriScheme, re);
        }
        if (clazz == null) {
            LOG.warn("No class configured for " + uriScheme + ", " + key + " is empty");
            throw new IllegalArgumentException("No class configured for " + uriScheme);
        }
        return clazz;
    }

    /**
     * Construct a custom journal manager.
     * The class to construct is taken from the configuration.
     * @param uri Uri to construct
     * @return The constructed journal manager
     * @throws IllegalArgumentException if no class is configured for uri
     */
    @VisibleForTesting
    JournalManager createJournal(URI uri) {
        Class<? extends JournalManager> clazz = getJournalClass(conf, uri.getScheme());
        try {
            Constructor<? extends JournalManager> cons = clazz.getConstructor(Configuration.class, URI.class, NamespaceInfo.class);
            return cons.newInstance(conf, uri, storage.getNamespaceInfo());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to construct journal, " + uri, e);
        }
    }

    @VisibleForTesting
    public // spy because a spy is a shallow copy
    void restart() {
    }

    /**
     * Return total number of syncs happened on this edit log.
     * @return long - count
     */
    public long internal$getTotalSyncCount27() {
        // Avoid NPE as possible.
        if (editLogStream == null) {
            return 0;
        }
        long count = 0;
        try {
            count = editLogStream.getNumSync();
        } catch (NullPointerException ignore) {
            // This method is used for metrics, so we don't synchronize it.
            // Therefore NPE can happen even if there is a null check before.
        }
        return count;
    }

    static FSEditLog newInstance(Configuration conf, NNStorage storage, List<URI> editsDirs) {
        try {
            if (!(storage.layoutVersion == storage.namespaceID)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(0);
            }
        } catch (Exception e) {
        }
        try {
            if (!(storage.layoutVersion == storage.cTime)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(1);
            }
        } catch (Exception e) {
        }
        try {
            if (!(storage.layoutVersion == org.zlab.dinv.runtimechecker.Quant.size(editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(2);
            }
        } catch (Exception e) {
        }
        try {
            if (!(editsDirs.size() == org.zlab.dinv.runtimechecker.Quant.size(editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(3);
            }
        } catch (Exception e) {
        }
        FSEditLog returnValue;
        returnValue = internal$newInstance0(conf, storage, editsDirs);
        try {
            if (!(storage.layoutVersion == storage.namespaceID)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(4);
            }
        } catch (Exception e) {
        }
        try {
            if (!(storage.layoutVersion == storage.cTime)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(5);
            }
        } catch (Exception e) {
        }
        try {
            if (!(storage.layoutVersion == org.zlab.dinv.runtimechecker.Quant.size(editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(6);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    public synchronized void initJournalsForWrite() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.UNINITIALIZED)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(7);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(8);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(9);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(10);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(11);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(12);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(13);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(14);
            }
        } catch (Exception e) {
        }
        internal$initJournalsForWrite1();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(15);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(16);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(17);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(18);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(19);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(20);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(21);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(22);
            }
        } catch (Exception e) {
        }
    }

    private synchronized void initJournals(List<URI> dirs) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.UNINITIALIZED)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(23);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(24);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(25);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(26);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(27);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(28);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(29);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(dirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(30);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(31);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs == dirs)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(32);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == dirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(33);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.getClass().getName() == dirs.getClass().getName())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(34);
            }
        } catch (Exception e) {
        }
        try {
            if (!(dirs.size() == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(35);
            }
        } catch (Exception e) {
        }
        try {
            if (!(dirs.size() == org.zlab.dinv.runtimechecker.Quant.size(dirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(36);
            }
        } catch (Exception e) {
        }
        internal$initJournals2(dirs);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.UNINITIALIZED)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(37);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == dirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(38);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(39);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(40);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(41);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(42);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(43);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(44);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(dirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(45);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(46);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == dirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(47);
            }
        } catch (Exception e) {
        }
        try {
            if (!(dirs.size() == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(48);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Get the list of URIs the editlog is using for storage
     * @return collection of URIs in use by the edit log
     */
    Collection<URI> getEditURIs() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.UNINITIALIZED)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(49);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(50);
            }
        } catch (Exception e) {
        }
        Collection<URI> returnValue;
        returnValue = internal$getEditURIs3();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.UNINITIALIZED)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(51);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(52);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Initialize the output stream for logging, opening the first
     * log segment.
     */
    synchronized void openForWrite(int layoutVersion) throws IOException {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(53);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(54);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(55);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(56);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(57);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(58);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(59);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(60);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.storage.layoutVersion == layoutVersion)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(61);
            }
        } catch (Exception e) {
        }
        internal$openForWrite4(layoutVersion);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(62);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(63);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(64);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(65);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(66);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(67);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(68);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(69);
            }
        } catch (Exception e) {
        }
    }

    /**
     * @return true if the log is currently open in write mode, regardless
     * of whether it actually has an open segment.
     */
    synchronized boolean isOpenForWrite() {
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(70);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(71);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(72);
            }
        } catch (Exception e) {
        }
        boolean returnValue;
        returnValue = internal$isOpenForWrite5();
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(73);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(74);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(75);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * @return true if the log is open in write mode and has a segment open
     * ready to take edits.
     */
    synchronized boolean isSegmentOpen() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(76);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(77);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(78);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(79);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(80);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(81);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(82);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(83);
            }
        } catch (Exception e) {
        }
        boolean returnValue;
        returnValue = internal$isSegmentOpen6();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(84);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(85);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(86);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(87);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(88);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(89);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(90);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(91);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Return true the state is IN_SEGMENT.
     * This method is not synchronized and must be used only for metrics.
     * @return true if the log is open in write mode and has a segment open
     * ready to take edits.
     */
    boolean isSegmentOpenWithoutLock() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(92);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(93);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(94);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(95);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(96);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(97);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(98);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(99);
            }
        } catch (Exception e) {
        }
        boolean returnValue;
        returnValue = internal$isSegmentOpenWithoutLock7();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(100);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(101);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(102);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(103);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(104);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(105);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(106);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(107);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    synchronized boolean doEditTransaction(final FSEditLogOp op) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(108);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(109);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(110);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(111);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == op.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(112);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(113);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(114);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(115);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(116);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(117);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(118);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(op.rpcClientId))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(119);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(120);
            }
        } catch (Exception e) {
        }
        boolean returnValue;
        returnValue = internal$doEditTransaction8(op);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(121);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(122);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(123);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(124);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(125);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(126);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(127);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(128);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(129);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(op.rpcClientId))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(130);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Signal that an automatic sync scheduling is done if it is scheduled
     */
    synchronized void doneWithAutoSyncScheduling() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(131);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(132);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(133);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(134);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(135);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(136);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(137);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(138);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(139);
            }
        } catch (Exception e) {
        }
        internal$doneWithAutoSyncScheduling9();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(140);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(141);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(142);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(143);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(144);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(145);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(146);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(147);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(148);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Check if should automatically sync buffered edits to
     * persistent store
     *
     * @return true if any of the edit stream says that it should sync
     */
    private boolean shouldForceSync() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(149);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(150);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(151);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(152);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(153);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(154);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(155);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(156);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(157);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(158);
            }
        } catch (Exception e) {
        }
        boolean returnValue;
        returnValue = internal$shouldForceSync10();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(159);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(160);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(161);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(162);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(163);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(164);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(165);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(166);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(167);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    protected void beginTransaction(final FSEditLogOp op) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(168);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(169);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(170);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(171);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(172);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(173);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(174);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(175);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(op.rpcClientId))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(176);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.curSegmentTxId == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(177);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.curSegmentTxId == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(178);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(179);
            }
        } catch (Exception e) {
        }
        internal$beginTransaction11(op);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(180);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(181);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(182);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(183);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == op.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(184);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(185);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(186);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(187);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(188);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(189);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(190);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(op.rpcClientId))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(191);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(192);
            }
        } catch (Exception e) {
        }
    }

    private void endTransaction(long start) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(193);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(194);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(195);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(196);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(197);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(198);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(199);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(200);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(201);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(202);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(203);
            }
        } catch (Exception e) {
        }
        internal$endTransaction12(start);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(204);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(205);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(206);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(207);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(208);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(209);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(210);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(211);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(212);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(213);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Return the transaction ID of the last transaction written to the log.
     */
    public synchronized long getLastWrittenTxId() {
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(214);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(215);
            }
        } catch (Exception e) {
        }
        long returnValue;
        returnValue = internal$getLastWrittenTxId13();
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(216);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Return the transaction ID of the last transaction written to the log.
     * This method is not synchronized and must be used only for metrics.
     * @return The transaction ID of the last transaction written to the log
     */
    long getLastWrittenTxIdWithoutLock() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(217);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(218);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(219);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(220);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(221);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(222);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(223);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(224);
            }
        } catch (Exception e) {
        }
        long returnValue;
        returnValue = internal$getLastWrittenTxIdWithoutLock14();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(225);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(226);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * @return the first transaction ID in the current log segment
     */
    @VisibleForTesting
    public synchronized long getCurSegmentTxId() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(227);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(228);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(229);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(230);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(231);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(232);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(233);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(234);
            }
        } catch (Exception e) {
        }
        long returnValue;
        returnValue = internal$getCurSegmentTxId15();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(235);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(236);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Return the first transaction ID in the current log segment.
     * This method is not synchronized and must be used only for metrics.
     * @return The first transaction ID in the current log segment
     */
    long getCurSegmentTxIdWithoutLock() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(237);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(238);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(239);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(240);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(241);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(242);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(243);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(244);
            }
        } catch (Exception e) {
        }
        long returnValue;
        returnValue = internal$getCurSegmentTxIdWithoutLock16();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(245);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(246);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Set the transaction ID to use for the next transaction written.
     */
    synchronized void setNextTxId(long nextTxId) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(247);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(248);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(249);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(250);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(251);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(252);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(253);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(254);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == nextTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(255);
            }
        } catch (Exception e) {
        }
        internal$setNextTxId17(nextTxId);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(256);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(257);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(258);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(259);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(260);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(261);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(262);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(263);
            }
        } catch (Exception e) {
        }
    }

    protected void logSync(long mytxid) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(264);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(265);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(266);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(267);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(268);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == mytxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(269);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(270);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(271);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(272);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(273);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(274);
            }
        } catch (Exception e) {
        }
        internal$logSync18(mytxid);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(275);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(276);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(277);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(278);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(279);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(280);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(281);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(282);
            }
        } catch (Exception e) {
        }
    }

    //
    private void printStatistics(boolean force) {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(283);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(284);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(285);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(286);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(287);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(288);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(289);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(290);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(291);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(292);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == force)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(293);
            }
        } catch (Exception e) {
        }
        internal$printStatistics19(force);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(294);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(295);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(296);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(297);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(298);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(299);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(300);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(301);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(302);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(303);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Used only by tests.
     */
    @VisibleForTesting
    public JournalSet getJournalSet() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(304);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(305);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(306);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(307);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(308);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(309);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(310);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(311);
            }
        } catch (Exception e) {
        }
        JournalSet returnValue;
        returnValue = internal$getJournalSet20();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(312);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(313);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(314);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(315);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(316);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(317);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(318);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(319);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Start writing to the log segment with the given txid.
     * Transitions from BETWEEN_LOG_SEGMENTS state to IN_LOG_SEGMENT state.
     */
    synchronized void startLogSegment(final long segmentTxId, boolean writeHeaderTxn, int layoutVersion) throws IOException {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(320);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(321);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(322);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(323);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(324);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(325);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(326);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(327);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.storage.layoutVersion == layoutVersion)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(328);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == segmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(329);
            }
        } catch (Exception e) {
        }
        internal$startLogSegment21(segmentTxId, writeHeaderTxn, layoutVersion);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(330);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(331);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(332);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(333);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(334);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(335);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(336);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(337);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Run recovery on all journals to recover any unclosed segments
     */
    synchronized void recoverUnclosedStreams() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(338);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(339);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(340);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(341);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(342);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(343);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(344);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(345);
            }
        } catch (Exception e) {
        }
        internal$recoverUnclosedStreams22();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(346);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(347);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(348);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(349);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(350);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(351);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(352);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(353);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void selectInputStreams(Collection<EditLogInputStream> streams, long fromTxId, boolean inProgressOk, boolean onlyDurableTxns) throws IOException {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(354);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(355);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(356);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(357);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == inProgressOk)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(358);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == onlyDurableTxns)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(359);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.numTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(360);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.totalTimeTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(361);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == fromTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(362);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.getClass().getName() == streams.getClass().getName())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(363);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.sharedEditsDirs.size() == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(364);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(365);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(366);
            }
        } catch (Exception e) {
        }
        internal$selectInputStreams23(streams, fromTxId, inProgressOk, onlyDurableTxns);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(367);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(368);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(369);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(370);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.numTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(371);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.totalTimeTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(372);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.sharedEditsDirs.size() == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(373);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(374);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(375);
            }
        } catch (Exception e) {
        }
    }

    public Collection<EditLogInputStream> selectInputStreams(long fromTxId, long toAtLeastTxId, MetaRecoveryContext recovery, boolean inProgressOK) throws IOException {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(376);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(377);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(378);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(379);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(380);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == toAtLeastTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(381);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(382);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(383);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(384);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == inProgressOK)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(385);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == fromTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(386);
            }
        } catch (Exception e) {
        }
        Collection<EditLogInputStream> returnValue;
        returnValue = internal$selectInputStreams24(fromTxId, toAtLeastTxId, recovery, inProgressOK);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(387);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(388);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(389);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(390);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(391);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(392);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(393);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(394);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Select a list of input streams.
     *
     * @param fromTxId first transaction in the selected streams
     * @param toAtLeastTxId the selected streams must contain this transaction
     * @param recovery recovery context
     * @param inProgressOk set to true if in-progress streams are OK
     * @param onlyDurableTxns set to true if streams are bounded
     *                        by the durable TxId
     */
    public Collection<EditLogInputStream> selectInputStreams(long fromTxId, long toAtLeastTxId, MetaRecoveryContext recovery, boolean inProgressOk, boolean onlyDurableTxns) throws IOException {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(395);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(396);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(397);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(398);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(399);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == toAtLeastTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(400);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(401);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(402);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(403);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == inProgressOk)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(404);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == onlyDurableTxns)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(405);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == fromTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(406);
            }
        } catch (Exception e) {
        }
        Collection<EditLogInputStream> returnValue;
        returnValue = internal$selectInputStreams25(fromTxId, toAtLeastTxId, recovery, inProgressOk, onlyDurableTxns);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(407);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(408);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(409);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.totalTimeTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(410);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.sharedEditsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(411);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(412);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(413);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(414);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }

    /**
     * Check for gaps in the edit log input stream list.
     * Note: we're assuming that the list is sorted and that txid ranges don't
     * overlap.  This could be done better and with more generality with an
     * interval tree.
     */
    private void checkForGaps(List<EditLogInputStream> streams, long fromTxId, long toAtLeastTxId, boolean inProgressOk) throws IOException {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(415);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(416);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(417);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(418);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == inProgressOk)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(419);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.numTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(420);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.totalTimeTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(421);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.size() == fromTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(422);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.editsDirs.getClass().getName() == streams.getClass().getName())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(423);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.sharedEditsDirs.size() == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(424);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == toAtLeastTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(425);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(426);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(427);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(streams))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(428);
            }
        } catch (Exception e) {
        }
        internal$checkForGaps26(streams, fromTxId, toAtLeastTxId, inProgressOk);
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.BETWEEN_LOG_SEGMENTS)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(429);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(430);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.synctxid == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(431);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(432);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.numTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(433);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.totalTimeTransactions == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(434);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.sharedEditsDirs.size() == streams.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(435);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs) - 1)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(436);
            }
        } catch (Exception e) {
        }
        try {
            if (!(streams.size() == org.zlab.dinv.runtimechecker.Quant.size(this.sharedEditsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(437);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Return total number of syncs happened on this edit log.
     * @return long - count
     */
    public long getTotalSyncCount() {
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(438);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(439);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(440);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(441);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(442);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(443);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(444);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(445);
            }
        } catch (Exception e) {
        }
        long returnValue;
        returnValue = internal$getTotalSyncCount27();
        try {
            if (!(this.state == org.apache.hadoop.hdfs.server.namenode.FSEditLog.State.IN_SEGMENT)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(446);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.journalSet.minimumRedundantJournals == this.txid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(447);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.synctxid)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(448);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.curSegmentTxId)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(449);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.numTransactions)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(450);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == this.editsDirs.size())) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(451);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.txid == org.zlab.dinv.runtimechecker.Quant.size(this.editsDirs))) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(452);
            }
        } catch (Exception e) {
        }
        try {
            if (!(this.isSyncRunning == this.isAutoSyncScheduled)) {
                org.zlab.dinv.runtimechecker.Runtime.addViolation(453);
            }
        } catch (Exception e) {
        }
        return returnValue;
    }
}
