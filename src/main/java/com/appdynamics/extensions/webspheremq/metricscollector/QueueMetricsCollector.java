/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.mq.MQException;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.pcf.*;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class QueueMetricsCollector extends MetricsCollector implements Runnable {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(QueueMetricsCollector.class);
	private final String artifact = "Queues";

	public QueueMetricsCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch) {
		this.metricsToReport = metricsToReport;
		this.monitorContextConfig = monitorContextConfig;
		this.agent = agent;
		this.metricWriteHelper = metricWriteHelper;
		this.queueManager = queueManager;
		this.countDownLatch = countDownLatch;
	}

	public void run() {
		try {
			this.process();
		} catch (TaskExecutionException e) {
			logger.error("Error in QueueMetricsCollector ", e);
		} finally {
			countDownLatch.countDown();
		}
	}

	@Override
	protected void publishMetrics() throws TaskExecutionException {
		logger.info("Collecting queue metrics...");
		List<Future> futures = Lists.newArrayList();
		Map<String, WMQMetricOverride>  metricsForInquireQCmd = getMetricsToReport(InquireQCmdCollector.COMMAND);
		if(!metricsForInquireQCmd.isEmpty()){
			futures.add(monitorContextConfig.getContext().getExecutorService().submit("InquireQCmdCollector", new InquireQCmdCollector(this,metricsForInquireQCmd)));
		}
		Map<String, WMQMetricOverride>  metricsForInquireQStatusCmd = getMetricsToReport(InquireQStatusCmdCollector.COMMAND);
		if(!metricsForInquireQStatusCmd.isEmpty()){
			futures.add(monitorContextConfig.getContext().getExecutorService().submit("InquireQStatusCmdCollector", new InquireQStatusCmdCollector(this,metricsForInquireQStatusCmd)));
		}
		Map<String, WMQMetricOverride>  metricsForResetQStatsCmd = getMetricsToReport(ResetQStatsCmdCollector.COMMAND);
		if(!metricsForResetQStatsCmd.isEmpty()){
			futures.add(monitorContextConfig.getContext().getExecutorService().submit("ResetQStatsCmdCollector", new ResetQStatsCmdCollector(this,metricsForResetQStatsCmd)));
		}
		for(Future f: futures){
			try {
				long timeout = 20;
				if(monitorContextConfig.getConfigYml().get("queueMetricsCollectionTimeoutInSeconds") != null){
					timeout = (Integer)monitorContextConfig.getConfigYml().get("queueMetricsCollectionTimeoutInSeconds");
				}
				f.get(timeout, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.error("The thread was interrupted ",e);
			} catch (ExecutionException e) {
				logger.error("Something unforeseen has happened ",e);
			} catch (TimeoutException e) {
				logger.error("Thread timed out ",e);
			}
		}
	}

	private Map<String, WMQMetricOverride> getMetricsToReport(String command) {
		Map<String, WMQMetricOverride> commandMetrics = Maps.newHashMap();
		if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
			logger.debug("There are no metrics configured for {}",command);
			return commandMetrics;
		}
		Iterator<String> itr = getMetricsToReport().keySet().iterator();
		while (itr.hasNext()) {
			String metrickey = itr.next();
			WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
			if(wmqOverride.getIbmCommand().equalsIgnoreCase(command)){
				commandMetrics.put(metrickey,wmqOverride);
			}
		}
		return commandMetrics;
	}

	@Override
	public String getAtrifact() {
		return artifact;
	}

	@Override
	public Map<String, WMQMetricOverride> getMetricsToReport() {
		return this.metricsToReport;
	}

	protected void processPCFRequestAndPublishQMetrics(String queueGenericName, PCFMessage request, String command) throws MQException, IOException {
		PCFMessage[] response;
		logger.debug("sending PCF agent request to query metrics for generic queue {} for command {}",queueGenericName,command);
		long startTime = System.currentTimeMillis();
		response = agent.send(request);
		long endTime = System.currentTimeMillis() - startTime;
		logger.debug("PCF agent queue metrics query response for generic queue {} for command {} received in {} milliseconds", queueGenericName, command,endTime);
		if (response == null || response.length <= 0) {
			logger.debug("Unexpected Error while PCFMessage.send() for command {}, response is either null or empty",command);
			return;
		}
		for (int i = 0; i < response.length; i++) {
			String last_get_date = "";
			String last_get_time = "";
			String last_put_date = "";
			String last_put_time = "";
			boolean last_get_date_time = false;
			boolean last_put_date_time = false;
			String tz;
			tz = queueManager.getTimeZone()== null?"UTC":queueManager.getTimeZone().trim();
			logger.debug("Timezone is set to {}",tz);
			String queueName = response[i].getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
			Set<ExcludeFilters> excludeFilters = this.queueManager.getQueueFilters().getExclude();
			if(!isExcluded(queueName,excludeFilters)) { //check for exclude filters
				logger.debug("Pulling out metrics for queue name {} for command {}",queueName,command);
				Iterator<String> itr = getMetricsToReport().keySet().iterator();
				List<Metric> metrics = Lists.newArrayList();
				while (itr.hasNext()) {
					String metrickey = itr.next();
					WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
					try{
						PCFParameter pcfParam = response[i].getParameter(wmqOverride.getConstantValue());
						if (pcfParam != null) {
							if(pcfParam instanceof MQCFIN){
								int metricVal = response[i].getIntParameterValue(wmqOverride.getConstantValue());
								Metric metric = createMetric(queueManager, metrickey, metricVal, wmqOverride, getAtrifact(), queueName, metrickey);
								metrics.add(metric);
							}else if (pcfParam instanceof MQCFST) {
								long metricVal = 0;

								String s_metricVal = response[i].getStringParameterValue(wmqOverride.getConstantValue()).trim();
								if (!s_metricVal.isEmpty()) {
									logger.debug("String value {} for : {} {}", s_metricVal, queueName, wmqOverride.getIbmConstant());

									if (wmqOverride.getConstantValue() == com.ibm.mq.constants.CMQCFC.MQCACF_LAST_GET_DATE) {
										last_get_date = s_metricVal;

									} else if (wmqOverride.getConstantValue() == com.ibm.mq.constants.CMQCFC.MQCACF_LAST_GET_TIME) {
										last_get_time = s_metricVal;

									} else if (wmqOverride.getConstantValue() == com.ibm.mq.constants.CMQCFC.MQCACF_LAST_PUT_DATE) {
										last_put_date = s_metricVal;

									} else if (wmqOverride.getConstantValue() == CMQCFC.MQCACF_LAST_PUT_TIME) {
										last_put_time = s_metricVal;

									} else {
										logger.debug("Skipping .. {}. Unsupported KPI.", wmqOverride.getConstantValue());
										continue;
									}


									Metric metric;

									if (!last_get_date.isEmpty() && !last_get_time.isEmpty() && !last_get_date_time) {
										SimpleDateFormat sdtf = new java.text.SimpleDateFormat("yyyy-MM-dd hh.mm.ss");
										String dateAndTime = last_get_date+" "+last_get_time;

										sdtf.setTimeZone(TimeZone.getTimeZone(tz));
										metricVal = 0;
										try {
											metricVal = sdtf.parse(dateAndTime).getTime() / 1000;


										} catch (ParseException e) {
											e.printStackTrace();
										}
										long epoch = System.currentTimeMillis() / 1000;
										long seconds_since_last_get = epoch - metricVal;

										metric = createMetric(queueManager, "last_get_date_time", metricVal, null, getAtrifact(), queueName, "last_get_date_time");
										metrics.add(metric);

										metric = createMetric(queueManager, "seconds_since_last_get", seconds_since_last_get, null, getAtrifact(), queueName, "seconds_since_last_get");
										metrics.add(metric);
										last_get_date_time = true;


									}

									if (!last_put_date.isEmpty() && !last_put_time.isEmpty() && !last_put_date_time) {
										SimpleDateFormat sdtf = new java.text.SimpleDateFormat("yyyy-MM-dd hh.mm.ss");
										String dateAndTime = last_put_date+" "+last_put_time;

										sdtf.setTimeZone(TimeZone.getTimeZone(tz));
										metricVal = 0;
										try {
											metricVal = sdtf.parse(dateAndTime).getTime() / 1000;


										} catch (ParseException e) {
											e.printStackTrace();
										}
										long epoch = System.currentTimeMillis() / 1000;
										long seconds_since_last_put = epoch - metricVal;

										metric = createMetric(queueManager, "last_put_date_time", metricVal, null, getAtrifact(), queueName, "last_put_date_time");
										metrics.add(metric);

										metric = createMetric(queueManager, "seconds_since_last_put", seconds_since_last_put, null, getAtrifact(), queueName, "seconds_since_last_put");
										metrics.add(metric);
										last_put_date_time = true;


									}
								}


							}
							else if(pcfParam instanceof MQCFIL){
								int[] metricVals = response[i].getIntListParameterValue(wmqOverride.getConstantValue());
								if(metricVals != null){
									int count=0;
									for(int val : metricVals){
										count++;
										Metric metric = createMetric(queueManager, metrickey+ "_" + Integer.toString(count), val, wmqOverride, getAtrifact(), queueName, metrickey+ "_" + Integer.toString(count));
										metrics.add(metric);
									}
								}
							}
						} else {
							logger.warn("PCF parameter is null in response for Queue: {} for metric: {} in command {}", queueName, wmqOverride.getIbmCommand(),command);
						}
					}
					catch (PCFException pcfe) {
						logger.error("PCFException caught while collecting metric for Queue: {} for metric: {} in command {}",queueName, wmqOverride.getIbmCommand(),command, pcfe);
					}

				}
				publishMetrics(metrics);
			}
			else{
				logger.debug("Queue name {} is excluded.",queueName);
			}
		}
	}
}
