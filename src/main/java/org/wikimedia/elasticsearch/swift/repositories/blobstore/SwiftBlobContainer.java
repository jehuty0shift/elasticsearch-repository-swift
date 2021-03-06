/*
 * Copyright 2017 Wikimedia and BigData Boutique
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wikimedia.elasticsearch.swift.repositories.blobstore;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.support.AbstractBlobContainer;
import org.elasticsearch.common.blobstore.support.PlainBlobMetaData;
import org.javaswift.joss.model.Directory;
import org.javaswift.joss.model.DirectoryOrObject;
import org.javaswift.joss.model.StoredObject;
import org.wikimedia.elasticsearch.swift.SwiftPerms;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.security.PrivilegedAction;
import java.util.Collection;

/**
 * Swift's implementation of the AbstractBlobContainer
 */
public class SwiftBlobContainer extends AbstractBlobContainer {
    // Our local swift blob store instance
    protected final SwiftBlobStore blobStore;

    // The root path for blobs. Used by buildKey to build full blob names
    protected final String keyPath;

    /**
     * Constructor
     * @param path The BlobPath to find blobs in
     * @param blobStore The blob store to use for operations
     */
    protected SwiftBlobContainer(BlobPath path, SwiftBlobStore blobStore) {
        super(path);
        this.blobStore = blobStore;
        String keyPath = path.buildAsString();
        if (!keyPath.isEmpty() && !keyPath.endsWith("/")) {
            keyPath = keyPath + "/";
        }
        this.keyPath = keyPath;
    }

    /**
     * Does a blob exist? Self-explanatory.
     */
    @Override
    public boolean blobExists(final String blobName) {
        return SwiftPerms.exec(new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return blobStore.swift().getObject(buildKey(blobName)).exists();
                }
        });
    }

    /**
     * Delete a blob. Straightforward.
     * @param blobName A blob to delete
     */
    @Override
    public void deleteBlob(final String blobName) throws IOException {
        SwiftPerms.exec(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                StoredObject object = blobStore.swift().getObject(buildKey(blobName));
                if (object.exists()) {
                    object.delete();
                }
                return null;
            }
        });
    }

    /**
     * Get the blobs matching a given prefix
     * @param blobNamePrefix The prefix to look for blobs with
     * @return blobs metadata
     */
    @Override
    public ImmutableMap<String, BlobMetaData> listBlobsByPrefix(@Nullable final String blobNamePrefix) {
        return SwiftPerms.exec(() -> {
            ImmutableMap.Builder<String, BlobMetaData> blobsBuilder = ImmutableMap.builder();
            Collection<DirectoryOrObject> files;
            if (blobNamePrefix != null) {
                files = blobStore.swift().listDirectory(new Directory(buildKey(blobNamePrefix), '/'));
            } else {
                files = blobStore.swift().listDirectory(new Directory(keyPath, '/'));
            }
            if (files != null && !files.isEmpty()) {
                for (DirectoryOrObject object : files) {
                    if (object.isObject()) {
                        String name = object.getName().substring(keyPath.length());
                        blobsBuilder.put(name, new PlainBlobMetaData(name, object.getAsObject().getContentLength()));
                    }
                }
            }
            return blobsBuilder.build();
        });
    }

    /**
     * Get all the blobs
     */
    @Override
    public ImmutableMap<String, BlobMetaData> listBlobs() {
        return listBlobsByPrefix(null);
    }

    /**
     * Build a key for a blob name, based on the keyPath
     * @param blobName The blob name to build a key for
     * @return the key
     */
    protected String buildKey(String blobName) {
        return keyPath + blobName;
    }

    @Override
    public void move(String sourceBlobname, String destinationBlobname) throws IOException {

        String source = buildKey(sourceBlobname);
        String target = buildKey(destinationBlobname);
        if (blobExists(sourceBlobname)) {
            blobStore.moveBlobStorage(source, target);
        }
    }

    /**
     * Fetch a given blob into a BufferedInputStream
     * @param blobName The blob name to read
     * @return a stream
     */
    @Override
    public InputStream readBlob(final String blobName) throws IOException {
        final InputStream is = SwiftPerms.exec(new PrivilegedAction<InputStream>() {
            @Override
            public InputStream run() {
                return new BufferedInputStream(blobStore.swift().getObject(buildKey(blobName)).downloadObjectAsInputStream(),
                        blobStore.bufferSizeInBytes());
            }
        });

        if (null == is) {
            throw new NoSuchFileException("Blob object [" + blobName + "] not found.");
        }

        return is;
    }

    @Override
    public void writeBlob(final String blobName, final InputStream in, final long blobSize) throws IOException {
        // need to remove old file if already exist
        deleteBlob(blobName);
        SwiftPerms.exec(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                blobStore.swift().getObject(buildKey(blobName)).uploadObject(in);
                return null;
            }
        });
    }
}
