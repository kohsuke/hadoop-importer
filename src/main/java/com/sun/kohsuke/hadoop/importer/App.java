/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package com.sun.kohsuke.hadoop.importer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.ipc.RemoteException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;

/**
 * This tool keeps a directory mirrored in HDFS.
 *
 * Run this tool repeatedly on the same directory and updates will be sent to the HDFS.
 * TODO: if files are removed from the source, remove them from HDFS.
 */
public class App {
    public static void main(String[] args) throws Exception {
        if(args.length!=3) {
            System.out.println("Usage: java -jar importer.jar [HDFS URL] [local directory] [HDFS directory]");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        conf.set("fs.default.name", args[0]);
        DFSClient dfs = new DFSClient(conf);

        File in = new File(args[1]);
        String out = args[2];

        File[] children = in.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isFile();
            }
        });
        if(children==null) {
            System.out.println("No such directory exists: "+in);
            System.exit(-1);
        }
        int cnt=1;
        for (File f : children) {
            String dest = out + '/' + f.getName();
            FileStatus i = dfs.getFileInfo(dest);
            if (i == null || i.getModificationTime() != f.lastModified() || i.getLen()!=f.length()) {
                System.out.printf("(%d/%d) Importing %s\n",cnt,children.length,f);
                try {
                    IOUtils.copyBytes(new FileInputStream(f), dfs.create(dest, true), conf);
                    dfs.setTimes(dest, f.lastModified(), f.lastModified());
                } catch (RemoteException e) {
                    // failure to create
                    e.printStackTrace();
                }
            } else {
                System.out.printf("(%d/%d) Skipping %s\n",cnt,children.length,f);
            }
            cnt++;
        }
    }
}
