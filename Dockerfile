# ----- Giai đoạn 1: Build ứng dụng với Maven -----
# Sử dụng image Maven với phiên bản JDK 17 để tương thích
FROM maven:3.8-openjdk-17 AS builder

# Đặt thư mục làm việc trong container build
WORKDIR /app

# Copy file pom.xml trước để tận dụng Docker cache nếu dependencies không đổi
COPY pom.xml .
# Tải dependencies (tùy chọn, có thể gộp vào lệnh package)
# RUN mvn dependency:go-offline

# Copy toàn bộ source code vào
COPY src ./src

# Chạy lệnh build Maven để tạo file JAR (bỏ qua tests)
RUN mvn clean package -DskipTests

# ----- Giai đoạn 2: Tạo image runtime cuối cùng -----
# Sử dụng image OpenJDK 17 như bạn đã chọn ban đầu
FROM openjdk:17

# Đặt thư mục làm việc trong container runtime
WORKDIR /app

# Copy file JAR đã được build từ giai đoạn 'builder'
# Docker sẽ tự tìm file JAR duy nhất trong target nếu dùng wildcard *.jar
# Đổi tên thành 'backend-service-stm.jar' như trong file gốc của bạn
COPY --from=builder /app/target/*.jar backend-service-stm.jar

# Mở port 8080 để Railway có thể truy cập ứng dụng
EXPOSE 8080

# Lệnh để khởi chạy ứng dụng khi container bắt đầu
ENTRYPOINT ["java", "-Xms128m", "-Xmx350m", "-jar", "backend-service-stm.jar"]
