/*Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 *Redistribution and use in source and binary forms, with or without
 *modification, are permitted provided that the following conditions are
 *met:
 *    * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 *THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.os;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class BinderTracker {

    private int checkPid;
    private static final String BINDERTRACKER = "binderTracker";
    private final String binderTransactionFile = "/d/binder/transactions";
    private ArrayList<Integer> binderPids = null;
    private ArrayList<ArrayList<Integer>> binderAllPids=null;
    private static final boolean DEBUG = false;

    public BinderTracker(int pid) {
        this.checkPid = pid;
        binderPids = new ArrayList<Integer>();
        binderAllPids = new ArrayList<ArrayList<Integer>>();
    }

    BinderTracker(){
    }

    private boolean binderFileIfExist() {
        File fileTransactions = new File(binderTransactionFile);
        if (fileTransactions.exists()) {
            return true;
        }
        Log.e(BINDERTRACKER, "binder transaction file not exist" + binderTransactionFile);
        return false;
    }


    /**
     * Get the pid who is binder communicating with the <pid>.
     * return a ArrayList.
     */
    public ArrayList<Integer> getBinderTransaction() {
        long startTime = System.currentTimeMillis();
        binderPids.clear();
        if (!binderFileIfExist()) {
            Log.e(BINDERTRACKER, "file not exist or unknown error!");
            return null;
        }
        ArrayList<Integer> listBinders = getBinderTransactionFile(checkPid, binderTransactionFile);
        if (listBinders == null || listBinders.size() == 0) {
            Log.e(BINDERTRACKER, "can't get any effective binder communication information !");
            return null;
        }

        ArrayList<Integer> binders = getAllBinders(listBinders);
        if (binders == null || binders.size() == 0) {
            Log.w(BINDERTRACKER, "Not found any binders communicated with " + checkPid + " or known error happened .");
            return null;
        }
        Log.i(BINDERTRACKER, "binderPids: size:" + binderPids.size() + ",content:" + binderPids);
        Log.i("Time", "total time is " + (System.currentTimeMillis() - startTime) + "ms");

        return binders;

    }

    /**
     * Step 1 : Parser binders transaction file.Put it in <binders>
     */
    public ArrayList<Integer> getBinderTransactionFile(int checkPid, String binderFile) {
        Log.i(BINDERTRACKER, "getBinderTransactionFile beginning! File : " + binderFile);
        BufferedReader bReader = null;
        ArrayList<Integer> binders = new ArrayList<Integer>();
        try {
            bReader = new BufferedReader(new FileReader(binderFile));
            String tmp = null;
            int line = 0;
            try {
                while ((tmp = bReader.readLine()) != null) {
                    if (tmp.contains("from") && tmp.contains("to")) {
                        if (DEBUG) {
                            Log.d(BINDERTRACKER, "a binder communication:" + tmp);
                        }
                        String arr[] = tmp.split("\\s+");
                        for (int i = 0; i < arr.length; i++) {
                            if ("from".equals(arr[i])) {
                                String first = (arr[i + 1].split(":")[0]);
                                String second = (arr[i + 3].split(":")[0]);
                                //check if it matches digital
                                boolean isDigital = (first.matches("[0-9]+") && (second.matches("[0-9]+")));
                                if (isDigital != true) {
                                    continue;
                                }
                                Integer firstBinder = Integer.valueOf(first);
                                Integer secondBinder = Integer.valueOf(second);
                                if (firstBinder != 0 && secondBinder != 0) {
                                    binders.add(firstBinder);
                                    binders.add(secondBinder);
                                }
                             break;
                            }
                        }
                    }
                }

                if (bReader != null) {
                    bReader.close();
                }
            } catch (IOException e) {
                Log.e(BINDERTRACKER, "parse failed");
            }
            return binders;
        } catch (FileNotFoundException e) {
            Log.e(BINDERTRACKER, "parse failed");
        }
        return null;
    }

    /**
     * Step 2 : Get all pids who are binder communicating with the <pid>.
     */
    public ArrayList<Integer> getAllBinders(ArrayList<Integer> binders) {

        ArrayList<Integer> anrBinders = new ArrayList<Integer>();
        int listSize = binders.size();

        //get all binders
        for (int i = 0; i < listSize; i++) {
            Integer tmp = binders.get(i);
            ArrayList<Integer> tmpList = new ArrayList<Integer>();
            for (int j = 0; j < listSize; j++) {
                if (tmp.equals(binders.get(j))) {
                    if (!(tmpList.contains(binders.get(j)))) {
                        tmpList.add(binders.get(j));
                    }
                    if (j % 2 == 0 && !(tmpList.contains(binders.get(j + 1)))) {
                        tmpList.add(binders.get(j + 1));
                    } else {
                        if (!(tmpList.contains(binders.get(j - 1)))) {
                            tmpList.add(binders.get(j - 1));
                        }
                    }
                }
            }
            if (!(binderAllPids.contains(tmpList))) {
                binderAllPids.add(tmpList);
            }

        }

        Log.i(BINDERTRACKER, "binderAllPids :" + binderAllPids.size() + "." + binderAllPids);
        anrBinders.add(checkPid);
        getBinderPids(anrBinders);

        return binderPids;
    }

    /**
     * Step 3: Get binders which communicating with <checkPid>
     */
    public void getBinderPids(ArrayList<Integer> anrBinders) {
        ArrayList<Integer> tmpBindersList = new ArrayList<Integer>();
        try {

            for (int j = 0; j < anrBinders.size(); j++) {
                for (int i = 0; i < binderAllPids.size(); i++) {
                    if (binderAllPids.get(i).get(0).equals(anrBinders.get(j))) {
                        for (int k = 0; k < binderAllPids.get(i).size(); k++) {
                            Integer tmp = binderAllPids.get(i).get(k);
                            if ((tmpBindersList.contains(tmp) || (binderPids.contains(tmp)))) {
                                continue;
                            }
                            Log.i(BINDERTRACKER, anrBinders.get(j) + " binder communication with: " + binderAllPids.get(i).get(k));
                            tmpBindersList.add(tmp);
                            binderPids.add(tmp);
                        }
                        continue;
                    }
                }
            }
            //no others binders communicated with <anr_pid> ,or have found out all binders communicated with <anr_pid>
            if (tmpBindersList.size() == 0) {
                return;
            }
            if (DEBUG) {
                Log.d(BINDERTRACKER, "binderPids: " + binderPids.size() + "," + binderPids);
            }
            getBinderPids(tmpBindersList);

        } catch (Exception e) {
            Log.e(BINDERTRACKER, "method getAll() happened unknown error!");
        }
    }
}
