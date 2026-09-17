import { useEffect, useState, useCallback } from 'react'

const API_BASE = 'http://localhost:8080/api'
const LOW_STOCK_THRESHOLD = 5

const PRODUCTS = [
  { id: 'P100', name: 'P100 - Wireless Mouse' },
  { id: 'P200', name: 'P200 - Mechanical Keyboard' },
  { id: 'P300', name: 'P300 - USB-C Hub' },
]

export default function App() {
  const [cart, setCart] = useState([]) // [{ productId, quantity }]
  const [selectedProduct, setSelectedProduct] = useState(PRODUCTS[0].id)
  const [selectedQty, setSelectedQty] = useState(1)

  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  const [lastResult, setLastResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const refreshAll = useCallback(async () => {
    try {
      const [invRes, ordersRes, notifRes] = await Promise.all([
        fetch(`${API_BASE}/inventory`),
        fetch(`${API_BASE}/orders`),
        fetch(`${API_BASE}/notifications`),
      ])
      setInventory(await invRes.json())
      setOrders(await ordersRes.json())
      setNotifications(await notifRes.json())
    } catch (err) {
      setError('Failed to refresh data: ' + err.message)
    }
  }, [])

  useEffect(() => {
    refreshAll()
  }, [refreshAll])

  function addToCart() {
    const qty = Number(selectedQty)
    if (qty <= 0) return
    setCart((prev) => {
      const existing = prev.find((i) => i.productId === selectedProduct)
      if (existing) {
        return prev.map((i) =>
          i.productId === selectedProduct ? { ...i, quantity: i.quantity + qty } : i
        )
      }
      return [...prev, { productId: selectedProduct, quantity: qty }]
    })
  }

  function removeFromCart(productId) {
    setCart((prev) => prev.filter((i) => i.productId !== productId))
  }

  async function submitOrder() {
    if (cart.length === 0) return
    setLoading(true)
    setError(null)
    setLastResult(null)

    try {
      const res = await fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ items: cart }),
      })

      if (!res.ok) {
        throw new Error(`Request failed with status ${res.status}`)
      }

      const data = await res.json()
      setLastResult(data)
      setCart([])
      await refreshAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function cancelOrder(orderId) {
    setError(null)
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/cancel`, { method: 'POST' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(`Cancel failed (${res.status}): ${text}`)
      }
      await refreshAll()
    } catch (err) {
      setError(err.message)
    }
  }

  function productName(productId) {
    return PRODUCTS.find((p) => p.id === productId)?.name ?? productId
  }

  return (
    <div style={styles.page}>
      <div style={styles.container}>
        <h1 style={styles.mainHeading}>Order + Inventory Dashboard</h1>

        {error && <div style={{ ...styles.result, ...styles.rejected }}>{error}</div>}

        <div style={styles.grid}>
          {/* Cart / place order */}
          <div style={styles.card}>
            <h2 style={styles.heading}>Place Order</h2>

            <div style={styles.row}>
              <select
                value={selectedProduct}
                onChange={(e) => setSelectedProduct(e.target.value)}
                style={styles.input}
              >
                {PRODUCTS.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              <input
                type="number"
                min="1"
                value={selectedQty}
                onChange={(e) => setSelectedQty(e.target.value)}
                style={{ ...styles.input, width: '70px' }}
              />
              <button onClick={addToCart} style={styles.secondaryButton}>
                Add
              </button>
            </div>

            {cart.length > 0 && (
              <ul style={styles.cartList}>
                {cart.map((item) => (
                  <li key={item.productId} style={styles.cartItem}>
                    <span>
                      {productName(item.productId)} x {item.quantity}
                    </span>
                    <button onClick={() => removeFromCart(item.productId)} style={styles.linkButton}>
                      remove
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <button
              onClick={submitOrder}
              disabled={loading || cart.length === 0}
              style={styles.button}
            >
              {loading ? 'Submitting...' : `Submit Order (${cart.length} item${cart.length === 1 ? '' : 's'})`}
            </button>

            {lastResult && (
              <div
                style={{
                  ...styles.result,
                  ...(lastResult.status === 'CONFIRMED' ? styles.confirmed : styles.rejected),
                }}
              >
                <p style={styles.status}>
                  Order #{lastResult.orderId}: {lastResult.status}
                </p>
                {lastResult.reason && <p>Reason: {lastResult.reason}</p>}
                <ul>
                  {lastResult.items?.map((it, idx) => (
                    <li key={idx}>
                      {productName(it.productId)} x {it.quantity} — {it.outcome}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* Live inventory */}
          <div style={styles.card}>
            <h2 style={styles.heading}>Inventory</h2>
            <table style={styles.table}>
              <thead>
                <tr>
                  <th style={styles.th}>Product</th>
                  <th style={styles.th}>Stock</th>
                </tr>
              </thead>
              <tbody>
                {inventory.map((item) => (
                  <tr
                    key={item.productId}
                    style={item.stock < LOW_STOCK_THRESHOLD ? styles.lowStockRow : undefined}
                  >
                    <td style={styles.td}>
                      {item.name} ({item.productId})
                    </td>
                    <td style={styles.td}>{item.stock}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Order history */}
          <div style={styles.card}>
            <h2 style={styles.heading}>Order History</h2>
            <div style={styles.scrollList}>
              {orders.map((order) => (
                <div key={order.orderId} style={styles.orderRow}>
                  <div>
                    <strong>#{order.orderId}</strong>{' '}
                    <span
                      style={{
                        ...styles.badge,
                        ...(order.status === 'CONFIRMED'
                          ? styles.badgeConfirmed
                          : order.status === 'CANCELLED'
                          ? styles.badgeCancelled
                          : styles.badgeRejected),
                      }}
                    >
                      {order.status}
                    </span>
                    <div style={styles.orderItems}>
                      {order.items?.map((it, idx) => (
                        <span key={idx}>
                          {productName(it.productId)} x {it.quantity}
                          {idx < order.items.length - 1 ? ', ' : ''}
                        </span>
                      ))}
                    </div>
                  </div>
                  {order.status === 'CONFIRMED' && (
                    <button onClick={() => cancelOrder(order.orderId)} style={styles.secondaryButton}>
                      Cancel
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Notification feed */}
          <div style={styles.card}>
            <h2 style={styles.heading}>Activity Feed</h2>
            <div style={styles.scrollList}>
              {notifications.map((n) => (
                <div key={n.notificationId} style={styles.notificationRow}>
                  {n.message}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

const styles = {
  page: {
    minHeight: '100vh',
    background: '#f4f5f7',
    fontFamily: 'system-ui, -apple-system, sans-serif',
    padding: '24px',
  },
  container: { maxWidth: '1000px', margin: '0 auto' },
  mainHeading: { marginBottom: '20px' },
  grid: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '20px',
  },
  card: {
    background: '#fff',
    padding: '20px',
    borderRadius: '12px',
    boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
  },
  heading: { marginTop: 0, marginBottom: '16px', fontSize: '18px' },
  row: { display: 'flex', gap: '8px', marginBottom: '12px' },
  input: {
    padding: '8px 10px',
    borderRadius: '6px',
    border: '1px solid #ccc',
    fontSize: '14px',
    flex: 1,
  },
  button: {
    marginTop: '8px',
    padding: '10px',
    borderRadius: '6px',
    border: 'none',
    background: '#2563eb',
    color: '#fff',
    fontWeight: 600,
    cursor: 'pointer',
    width: '100%',
  },
  secondaryButton: {
    padding: '8px 12px',
    borderRadius: '6px',
    border: '1px solid #2563eb',
    background: '#fff',
    color: '#2563eb',
    fontWeight: 600,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },
  linkButton: {
    background: 'none',
    border: 'none',
    color: '#b3261e',
    cursor: 'pointer',
    fontSize: '12px',
    textDecoration: 'underline',
  },
  cartList: { listStyle: 'none', padding: 0, margin: '0 0 12px 0' },
  cartItem: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '6px 0',
    borderBottom: '1px solid #eee',
    fontSize: '14px',
  },
  result: { marginTop: '16px', padding: '12px 14px', borderRadius: '8px', fontSize: '14px' },
  confirmed: { background: '#e6f7ec', border: '1px solid #34a853', color: '#1e7e34' },
  rejected: { background: '#fdecea', border: '1px solid #ea4335', color: '#b3261e' },
  status: { margin: 0, fontSize: '16px', fontWeight: 700 },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '14px' },
  th: { textAlign: 'left', padding: '8px', borderBottom: '2px solid #eee' },
  td: { padding: '8px', borderBottom: '1px solid #eee' },
  lowStockRow: { background: '#fdecea' },
  scrollList: { maxHeight: '300px', overflowY: 'auto' },
  orderRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '10px 0',
    borderBottom: '1px solid #eee',
    fontSize: '14px',
  },
  orderItems: { fontSize: '12px', color: '#666', marginTop: '4px' },
  badge: {
    fontSize: '11px',
    fontWeight: 700,
    padding: '2px 8px',
    borderRadius: '10px',
    marginLeft: '6px',
  },
  badgeConfirmed: { background: '#e6f7ec', color: '#1e7e34' },
  badgeRejected: { background: '#fdecea', color: '#b3261e' },
  badgeCancelled: { background: '#f0f0f0', color: '#666' },
  notificationRow: {
    padding: '8px 0',
    borderBottom: '1px solid #eee',
    fontSize: '13px',
    color: '#333',
  },
}
