# Sử dụng bản JRE nhẹ hơn nhiều so với JDK đầy đủ
FROM eclipse-temurin:17-jre-alpine

# Thiết lập thư mục làm việc
WORKDIR /app

# Tìm và copy file jar có trong thư mục target
COPY target/*.jar app.jar

# Mở cổng 8080
EXPOSE 8080

# Chạy ứng dụng với cấu hình tối ưu RAM cho máy ảo nhỏ
ENTRYPOINT ["java", "-Xmx2g", "-jar", "app.jar"]