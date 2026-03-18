# JASDAQ - High-Frequency Trading System

JASDAQ is a high-performance, real-time stock trading engine and dashboard. It features a robust matching engine written in Java, a modern React-based frontend, and a Django admin panel for platform management.

## System Architecture

- **Frontend**: React.js (Vite, TailwindCSS, Recharts, SockJS/STOMP)
- **Backend**: Spring Boot (Java, Maven, WebSockets, JPA)
- **Admin**: Django (Python, MySQL Connector)
- **Database**: MySQL (Primary storage)
- **Middleware**: 
  - **Redis**: Real-time caching and broadcast support
  - **Kafka**: Asynchronous event streaming and message persistence

---

## Getting Started

### 1. Prerequisites
Ensure you have the following installed:
- **Java 17+**
- **Node.js & npm**
- **Python 3.10+**
- **MySQL Server**
- **Redis Server**
- **Apache Kafka**

### 2. Infrastructure Setup (Databases & Messaging)

#### MySQL
Create the database:
```sql
CREATE DATABASE jasdaqdb;
```
*User/Password defaults: `root` / `root`*

#### Redis
Start the Redis server:
```bash
redis-server
```

#### Kafka
Start Zookeeper and Kafka (Standard local setup):
```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka Broker
bin/kafka-server-start.sh config/server.properties
```

---

### 3. Server Startup Sequence

#### Backend: Spring Boot
Navigate to the `Jasdaq` directory:
```bash
cd Jasdaq
./mvnw spring-boot:run
```
*Backend runs on: `http://localhost:8080`*

#### Frontend: React
Navigate to the `frontend` directory:
```bash
cd frontend
npm install
npm run dev
```
*Frontend runs on: `http://localhost:5173`*

#### Admin Panel: Django
Navigate to the `AdminHandler` directory:
```bash
cd AdminHandler
pip install django mysqlclient
python manage.py runserver 8000
```
*Admin runs on: `http://localhost:8000`*

---

### 4. Stopping Services

To stop the running services, you can usually use `Ctrl + C` in the respective terminal windows. For a more explicit shutdown:

#### Applications (Spring Boot, React, Django)
- Press `Ctrl + C` in each terminal window to stop the process.

#### Redis
```bash
redis-cli shutdown
```

#### Kafka & Zookeeper
Navigate to the Kafka directory:
```bash
# Stop Kafka Broker
bin/kafka-server-stop.sh

# Stop Zookeeper
bin/zookeeper-server-stop.sh
```

---

## API Documentation

### REST API (Spring Boot)
Base Web URL: `http://localhost:8080/api/v1`

| Endpoint | Method | Description |
|---|---|---|
| `/allCompanies` | `GET` | List all available companies and stock symbols. |
| `/{companyId}` | `GET` | Get detailed info for a single company. |
| `/{companyId}/metrics` | `GET` | Get real-time order book metrics (Bid/Ask volume). |

### WebSockets (Stomp/SockJS)
Endpoint: `ws://localhost:8080/ws`

| Topic / Destination | Type | Description |
|---|---|---|
| `/app/placeOrder` | `SEND` | Submit a new BUY/SELL order (Limit/Market). |
| `/app/cancelOrder` | `SEND` | Request cancellation of a pending order. |
| `/topic/orders` | `SUB` | Receive order status updates and confirmations. |
| `/topic/market-updates` | `SUB` | Receive live price updates for stocks. |
| `/topic/order-rejected` | `SUB` | Receive error/rejection notifications. |

---

## Database Schema

| Table | key Columns |
|---|---|
| **Companies** | `company_id`, `name`, `symbol`, `current_price` |
| **Orders** | `order_id`, `company_id`, `shares`, `price`, `status`, `market_limit` |
| **Trades** | `trade_id`, `buyer_id`, `seller_id`, `execution_price`, `quantity` |