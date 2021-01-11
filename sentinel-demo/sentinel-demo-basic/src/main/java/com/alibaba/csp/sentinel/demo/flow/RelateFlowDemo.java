/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.demo.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

/**
 * @author jialiang.linjl
 */
public class RelateFlowDemo {

    private static final String READKEY = "read";
    private static final String WRITEKEY = "write";

    private static AtomicInteger readPass = new AtomicInteger();
    private static AtomicInteger readBlock = new AtomicInteger();
    private static AtomicInteger readTotal = new AtomicInteger();

    private static AtomicInteger writePass = new AtomicInteger();
    private static AtomicInteger writeBlock = new AtomicInteger();
    private static AtomicInteger writeTotal = new AtomicInteger();

    private static volatile boolean stop = false;

    private static final int threadCount = 32;

    private static int seconds = 60 + 40;

    public static void main(String[] args) throws Exception {
        initFlowQpsRule();

        tick();
        // first make the system run on a very low condition
        read();
        write();

        System.out.println("===== begin to do flow control");
        System.out.println("only 20 requests per second can pass");

    }

    private static void initFlowQpsRule() {
        List<FlowRule> rules = new ArrayList<FlowRule>();
        FlowRule rule1 = new FlowRule();
        rule1.setResource(READKEY);
        // set limit qps to 20
        rule1.setCount(20);
        rule1.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule1.setLimitApp("default");

        FlowRule rule2 = new FlowRule();
        rule2.setResource(READKEY);
        // set limit qps to 20
        rule2.setCount(20);
        rule2.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule2.setStrategy(RuleConstant.STRATEGY_RELATE);
        rule2.setRefResource(WRITEKEY);
        rule2.setLimitApp("default");

        rules.add(rule1);
        FlowRuleManager.loadRules(rules);
    }

    private static void read() {
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(new ReadRunTask());
            t.setName("simulate-traffic-Task");
            t.start();
        }
    }

    private static void write() {
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(new WriteRunTask());
            t.setName("simulate-traffic-Task");
            t.start();
        }
    }

    private static void tick() {
        Thread timer = new Thread(new TimerTask());
        timer.setName("sentinel-timer-task");
        timer.start();
    }

    static class TimerTask implements Runnable {

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            System.out.println("begin to statistic!!!");

            long readOldTotal = 0;
            long readOldPass = 0;
            long readOldBlock = 0;
            long writeOldTotal = 0;
            long writeOldPass = 0;
            long writeOldBlock = 0;
            while (!stop) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
                long readGlobalTotal = readTotal.get();
                long readOneSecondTotal = readGlobalTotal - readOldTotal;
                readOldTotal = readGlobalTotal;

                long readGlobalPass = readPass.get();
                long oneSecondPass = readGlobalPass - readOldPass;
                readOldPass = readGlobalPass;

                long globalBlock = readBlock.get();
                long oneSecondBlock = globalBlock - readOldBlock;
                readOldBlock = globalBlock;

                //System.out.println("readTime:" + seconds + " read qps is: " + readOneSecondTotal);
                System.out.println(TimeUtil.currentTimeMillis() + ", readTotal:" + readOneSecondTotal
                        + ", readPass:" + oneSecondPass
                        + ", readBlock:" + oneSecondBlock);


                long writeGlobalTotal = writeTotal.get();
                long writeOneSecondTotal = writeGlobalTotal - writeOldTotal;
                writeOldTotal = writeGlobalTotal;

                long writeGlobalPass = writePass.get();
                long writeOneSecondPass = writeGlobalPass - writeOldPass;
                writeOldPass = writeGlobalPass;

                long writeGlobalBlock = writeBlock.get();
                long writeOneSecondBlock = writeGlobalBlock - writeOldBlock;
                writeOldBlock = writeGlobalBlock;

                //System.out.println("writeTime:" + seconds + " write qps is: " + writeOneSecondTotal);
                System.out.println(TimeUtil.currentTimeMillis() + ", writeTotal:" + writeOneSecondTotal
                        + ", writePass:" + writeOneSecondPass
                        + ", writeBlock:" + writeOneSecondBlock);

                if (seconds-- <= 0) {
                    stop = true;
                }
            }

            long cost = System.currentTimeMillis() - start;
            // System.out.println("time cost: " + cost + " ms");
            System.out.println("readTotal:" + readTotal.get() + ", readPass:" + readPass.get()
                    + ", readBlock:" + readBlock.get());

            System.out.println("writeTotal:" + writeTotal.get() + ", writePass:" + writePass.get()
                    + ", writeBlock:" + writeBlock.get());
            System.exit(0);
        }
    }

    static class ReadRunTask implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                Entry entry = null;

                try {
                    entry = SphU.entry(READKEY);
                    // token acquired, means pass
                    readPass.addAndGet(1);
                } catch (BlockException e1) {
                    readBlock.incrementAndGet();
                } catch (Exception e2) {
                    // biz exception
                } finally {
                    readTotal.incrementAndGet();
                    if (entry != null) {
                        entry.exit();
                    }
                }

                Random random2 = new Random();
                try {
                    TimeUnit.MILLISECONDS.sleep(random2.nextInt(50));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    static class WriteRunTask implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                Entry entry = null;

                try {
                    entry = SphU.entry(WRITEKEY);
                    // token acquired, means pass
                    writePass.addAndGet(1);
                } catch (BlockException e1) {
                    writeBlock.incrementAndGet();
                } catch (Exception e2) {
                    // biz exception
                } finally {
                    writeTotal.incrementAndGet();
                    if (entry != null) {
                        entry.exit();
                    }
                }

                Random random2 = new Random();
                try {
                    TimeUnit.MILLISECONDS.sleep(random2.nextInt(50));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }
}
