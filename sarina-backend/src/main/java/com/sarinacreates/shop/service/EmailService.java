package com.sarinacreates.shop.service;

import com.sarinacreates.shop.model.Order;
import com.sarinacreates.shop.model.OrderItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final String STUDIO_EMAIL = "sarinaquadri71@gmail.com";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendNewOrderNotification(Order order) {
        StringBuilder itemsSummary = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            itemsSummary.append("  • ").append(item.getName())
                    .append(" (x").append(item.getQty()).append(") - ₹")
                    .append(item.getPrice() * item.getQty()).append("\n");
        }

        String subject = "🎨 NEW ORDER RECEIVED: #" + order.getId() + " - ₹" + order.getTotal();
        String text = "Hello Studio Admin,\n\n" +
                "You have received a new order on sarinacreates!\n\n" +
                "Order Details:\n" +
                "----------------------------------------\n" +
                "Order ID: " + order.getId() + "\n" +
                "Total Amount: ₹" + order.getTotal() + "\n" +
                "Status: " + order.getStatus() + "\n\n" +
                "Customer Information:\n" +
                "Name: " + (order.getCustomer() != null ? order.getCustomer().getName() : "N/A") + "\n" +
                "Email: " + (order.getCustomer() != null ? order.getCustomer().getEmail() : "N/A") + "\n" +
                "Phone: " + (order.getCustomer() != null ? order.getCustomer().getPhone() : "N/A") + "\n" +
                "Address: " + (order.getCustomer() != null ? order.getCustomer().getAddress() : "N/A") + "\n\n" +
                "Items Ordered:\n" + itemsSummary.toString() + "\n" +
                "----------------------------------------\n" +
                "Manage this order in your studio dashboard: /admin/dashboard.html\n\n" +
                "sarinaCreates Automated Order Notification";

        // Print explicit, visible server logs
        System.out.println("=========================================================");
        System.out.println("📧 [EMAIL NOTIFICATION TRIGGERED]");
        System.out.println("To: " + STUDIO_EMAIL);
        System.out.println("Subject: " + subject);
        System.out.println("Customer Email: " + (order.getCustomer() != null ? order.getCustomer().getEmail() : "N/A"));
        System.out.println("=========================================================");

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(STUDIO_EMAIL);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);

                // Also send customer confirmation email if customer email is valid
                if (order.getCustomer() != null && order.getCustomer().getEmail() != null && !order.getCustomer().getEmail().isBlank()) {
                    SimpleMailMessage custMsg = new SimpleMailMessage();
                    custMsg.setTo(order.getCustomer().getEmail());
                    custMsg.setSubject("Order Confirmation - sarinacreates #" + order.getId());
                    custMsg.setText("Thank you for your order!\n\n" + text);
                    mailSender.send(custMsg);
                }

                System.out.println("✅ [EMAIL SENT SUCCESSFULLY] Email dispatched via SMTP to " + STUDIO_EMAIL);
            } catch (Exception e) {
                System.err.println("⚠️ [EMAIL SMTP LOG] SMTP mail sender error (configure mail host/pass in .env for live dispatch): " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️ [EMAIL LOG] JavaMailSender active in log-recording mode. Configure SMTP host/username/password to send live emails.");
        }
    }
}
