package com.drajer.bsa.scheduler;

import com.drajer.bsa.model.BsaTypes;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ScheduledJobData implements Serializable {

  UUID karExecutionStateId;
  String actionId;
  BsaTypes.ActionType actionType;
  String jobId;
  transient Instant expirationTime;
  Map<String, String> mdcContext;
  String xRequestId;

  ScheduledJobData(
      UUID id,
      String action,
      BsaTypes.ActionType type,
      Instant t,
      String job,
      String xReqId,
      Map<String, String> mdc) {
    karExecutionStateId = id;
    actionId = action;
    actionType = type;
    expirationTime = t;
    jobId = job;
    xRequestId = xReqId;
    mdcContext = mdc;
  }

  public UUID getKarExecutionStateId() {
    return karExecutionStateId;
  }

  public void setKarExecutionStateId(UUID karExecutionStateId) {
    this.karExecutionStateId = karExecutionStateId;
  }

  public String getActionId() {
    return actionId;
  }

  public void setActionId(String actionId) {
    this.actionId = actionId;
  }

  public BsaTypes.ActionType getActionType() {
    return actionType;
  }

  public void setActionType(BsaTypes.ActionType actionType) {
    this.actionType = actionType;
  }

  public Instant getExpirationTime() {
    return expirationTime;
  }

  public void setExpirationTime(Instant expirationTime) {
    this.expirationTime = expirationTime;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public Map<String, String> getMdcContext() {
    return mdcContext;
  }

  public void setMdcContext(Map<String, String> mdcContext) {
    this.mdcContext = mdcContext;
  }

  public String getxRequestId() {
    return xRequestId;
  }

  public void setxRequestId(String xRequestId) {
    this.xRequestId = xRequestId;
  }
}