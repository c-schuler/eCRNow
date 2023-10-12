package com.drajer.bsa.service.impl;

import com.drajer.bsa.dao.PhMessageDao;
import com.drajer.bsa.model.PublicHealthMessage;
import com.drajer.bsa.service.PhMessageService;
import com.drajer.sof.model.PublicHealthMessageData;
import java.util.List;
import java.util.Map;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PhMessageServiceImpl implements PhMessageService {

  @Autowired PhMessageDao phMessageDao;

  public List<PublicHealthMessage> getPhMessageData(Map<String, String> searchParams) {
    return phMessageDao.getPhMessageData(searchParams);
  }

  public List<PublicHealthMessage> getPhMessageDataSummary(Map<String, String> searchParams) {
    return phMessageDao.getPhMessageDataSummary(searchParams);
  }

  public List<PublicHealthMessage> getPhMessageDataByXRequestIds(List<String> xRequestIds) {
    return phMessageDao.getPhMessageByXRequestIds(xRequestIds);
  }

  public List<PublicHealthMessage> getPhMessagesContainingXRequestIds(List<String> xRequestIds) {
    return phMessageDao.getPhMessagesContainingXRequestIds(xRequestIds);
  }

  public List<PublicHealthMessage> getPhMessageByParameters(
      PublicHealthMessageData publicHealthMessageData) {
    return phMessageDao.getPhMessageByParameters(publicHealthMessageData);
  }

  public void deletePhMessage(PublicHealthMessage publicHealthMessage) {
    phMessageDao.delete(publicHealthMessage);
  }
}
