package com.civic_connect.backend.notification.service;

import com.civic_connect.backend.notification.entity.Notification;
import com.civic_connect.backend.notification.repository.NotificationRepository;
import com.civic_connect.backend.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
 private final NotificationRepository notifications;
 public NotificationService(NotificationRepository notifications){
  this.notifications=notifications;
 }
 public void send(User user,String title,String message){
  Notification notification=new Notification();
  notification.setUser(user);
  notification.setTitle(title);
  notification.setMessage(message);
  notifications.save(notification);
 }
}
