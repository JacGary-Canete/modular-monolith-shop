# Lab 2: Extending the Modular Monolith - Order + Inventory + Notification

Stack: Spring Boot (Java) + React (Vite) + Supabase (Postgres)

Builds on Lab 1. Adds multi-item orders with all-or-nothing rollback, order
cancellation with restock, read endpoints for a live dashboard, an
in-monolith event-driven Notification module, and a low-stock alert rule.

## Project structure

```
backend/    Spring Boot app
  edu.cit.canete.shop            Order module
  edu.cit.canete.shop.event      OrderPlacedEvent / OrderRejectedEvent
  edu.cit.canete.inventory       Inventory module
  edu.cit.canete.inventory.event LowStockEvent
  edu.cit.canete.notification    Notification module (new)
frontend/   React (Vite) client - cart, inventory table, order history, activity feed
sql/        schema.sql - full schema (orders, order_items, inventory, notifications)
docs/       Network tab screenshots
```

## 1. Supabase setup

Same Supabase project as Lab 1 (if you kept it) or a new one:

1. Go to **Project Settings → Database → Connection pooling** and use the
   **Session pooler** connection string (not the direct `db.xxx.supabase.co`
   host - that requires IPv6, which many networks don't support).
2. Open the **SQL Editor** and run `sql/schema.sql`. This DROPS and
   recreates every table from scratch (inventory, orders, order_items,
   notifications), enables Row Level Security on all four, and reseeds:
   - P100 Wireless Mouse, stock 25
   - P200 Mechanical Keyboard, stock 10
   - P300 USB-C Hub, stock 0

   ⚠️ This will wipe any existing Lab 1 order history in that project,
   since the `orders` table structure has changed (single product/quantity
   columns are replaced by the `order_items` table).

## 2. Backend setup

```powershell
$env:DB_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres"
$env:DB_USER="postgres.<your-project-ref>"
$env:DB_PASSWORD="<your-db-password>"
cd backend
mvn spring-boot:run
```

API runs on `http://localhost:8080`.

## 3. Frontend setup

```powershell
cd frontend
npm install
npm run dev
```

App runs on `http://localhost:5173`.

## 4. API summary

| Method | Path                        | Description                              |
|--------|------------------------------|-------------------------------------------|
| POST   | `/api/orders`                | Place a multi-item order                 |
| GET    | `/api/orders`                | Order history                            |
| POST   | `/api/orders/{orderId}/cancel` | Cancel order, restock its items        |
| GET    | `/api/inventory`             | Current stock for all products           |
| GET    | `/api/notifications`         | Activity feed (order + low-stock events) |

`POST /api/orders` request body:
```json
{ "items": [ { "productId": "P100", "quantity": 2 }, { "productId": "P200", "quantity": 1 } ] }
```

## 5. Testing checklist

- [ ] **Multi-item order, all succeed**: add 2+ products to the cart with
      valid quantities, submit. Expect `CONFIRMED`, every item `RESERVED`,
      inventory table updates for all items.
- [ ] **Multi-item order, one fails**: add one product with a quantity
      within stock and another with quantity exceeding its stock, submit.
      Expect `REJECTED`, and confirm in the inventory table that **no**
      item's stock changed, not even the one that would have succeeded.
- [ ] **Cancel + restock**: confirm an order, note the stock, cancel it
      from the Order History list, confirm `GET /api/inventory` (the table
      on screen) shows the stock restored.
- [ ] **Notification feed**: after the above, confirm the Activity Feed
      shows a "confirmed" entry, a "rejected" entry, and, if you drove a
      product below 5 units, a "Reorder needed" entry.

### Network tab evidence

**Multi-item order, CONFIRMED:**
![Multi-item confirmed](docs/multi-confirmed.png)

**Multi-item order, REJECTED (no partial reservation):**
![Multi-item rejected](docs/multi-rejected.png)

**Cancel + restock reflected in GET /api/inventory:**
![Cancel and restock](docs/cancel-restock.png)

**Notification feed (confirmed, rejected, low-stock):**
![Notification feed](docs/notifications.png)

---

## Reflection (300-500 words)

_Answer these in your own words based on what you actually built and tested._

### 1. Atomicity of multi-item orders in-process

Multi-item orders now call `InventoryService.reserve()` once per line item
within a single request. What in your code makes this stay atomic while
everything runs in one Spring Boot app (hint: look at `@Transactional` on
`OrderService.placeOrder()`, and the fact that you validate every item
*before* reserving any of them)? If Order and Inventory were split across a
network, each `reserve()` call would be a separate remote call with no
shared transaction. What would you need to add to keep multi-item orders
safe in that world (sagas, compensating "release" calls, idempotency keys
so a retried reserve doesn't double-reserve)?

_Your answer here._

### 2. Event publishing vs. direct calls (coupling)

`OrderService` never imports anything from `edu.cit.canete.notification` -
it publishes `OrderPlacedEvent` / `OrderRejectedEvent` via
`ApplicationEventPublisher`, and `NotificationEventListener` picks them up
with `@EventListener`. Compare this to how `OrderService` talks to
`InventoryService`: a direct interface call vs. publish-and-forget. What
does OrderService know about Notification today (hint: basically nothing -
it doesn't even know Notification exists)? If Notification became its own
microservice, what would you need that you don't have now (a message
broker like Kafka/RabbitMQ so events survive a Notification outage,
at-least-once delivery guarantees, handling duplicate event delivery)?

_Your answer here._

### 3. Which module to extract first

You now have three modules (Order, Inventory, Notification) and two event
types. If you had to pull exactly one out into its own microservice first,
which would you pick, and why? Consider: which module has the least
coupling to the others already (Notification, since it only listens to
events and is never called directly)? Which would benefit most from
independent scaling or ownership? What code changes would be needed, given
what you already built, to make that module's dependents (Order/Inventory)
talk to it over the network instead of in-process?

_Your answer here._

---

## Notes on design decisions

- **Event listeners run synchronously** (Spring's default, no `@Async`
  used). This keeps notification writes inside the same request lifecycle
  as the event that triggered them, so nothing is lost if the app restarts
  between the event firing and the listener running. The tradeoff is that
  a slow notification write would add latency to the order request itself,
  but since this is a simple database insert, that cost is negligible here.
- **`InventoryServiceImpl` and `NotificationEventListener` are both
  package-private**, consistent with Lab 1's module boundary rule: nothing
  outside their own package can reference the concrete class, only the
  public interface (`InventoryService`) or the event types.
