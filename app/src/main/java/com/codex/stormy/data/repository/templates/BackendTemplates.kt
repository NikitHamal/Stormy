package com.codex.stormy.data.repository.templates

/**
 * Express.js REST API template files
 */
object ExpressTemplates {
    val INDEX_JS = """
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
require('dotenv').config();

const apiRoutes = require('./routes/api');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(morgan('dev'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// API Routes
app.use('/api', apiRoutes);

// Health check
app.get('/health', (req, res) => {
    res.json({
        status: 'healthy',
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// Root route
app.get('/', (req, res) => {
    res.json({
        message: 'Welcome to the API',
        version: '1.0.0',
        endpoints: {
            health: '/health',
            api: '/api',
            users: '/api/users',
            items: '/api/items'
        }
    });
});

// 404 handler
app.use((req, res) => {
    res.status(404).json({
        error: 'Not Found',
        message: `Route ${'$'}{req.method} ${'$'}{req.path} not found`
    });
});

// Error handler
app.use((err, req, res, next) => {
    console.error(err.stack);
    res.status(err.status || 500).json({
        error: err.name || 'Internal Server Error',
        message: err.message || 'Something went wrong'
    });
});

app.listen(PORT, () => {
    console.log(`Server running on http://localhost:${'$'}{PORT}`);
    console.log(`Environment: ${'$'}{process.env.NODE_ENV || 'development'}`);
});

module.exports = app;
    """.trimIndent()

    val API_ROUTES = """
const express = require('express');
const router = express.Router();

// In-memory data stores (replace with database in production)
let users = [
    { id: 1, name: 'John Doe', email: 'john@example.com', createdAt: new Date().toISOString() },
    { id: 2, name: 'Jane Smith', email: 'jane@example.com', createdAt: new Date().toISOString() }
];

let items = [
    { id: 1, name: 'Item 1', description: 'First item', price: 9.99, createdAt: new Date().toISOString() },
    { id: 2, name: 'Item 2', description: 'Second item', price: 19.99, createdAt: new Date().toISOString() }
];

let nextUserId = 3;
let nextItemId = 3;

// ============ Users API ============

// GET all users
router.get('/users', (req, res) => {
    const { limit = 10, offset = 0 } = req.query;
    const paginatedUsers = users.slice(Number(offset), Number(offset) + Number(limit));

    res.json({
        data: paginatedUsers,
        total: users.length,
        limit: Number(limit),
        offset: Number(offset)
    });
});

// GET user by ID
router.get('/users/:id', (req, res) => {
    const user = users.find(u => u.id === Number(req.params.id));

    if (!user) {
        return res.status(404).json({ error: 'User not found' });
    }

    res.json(user);
});

// POST create user
router.post('/users', (req, res) => {
    const { name, email } = req.body;

    if (!name || !email) {
        return res.status(400).json({ error: 'Name and email are required' });
    }

    const newUser = {
        id: nextUserId++,
        name,
        email,
        createdAt: new Date().toISOString()
    };

    users.push(newUser);
    res.status(201).json(newUser);
});

// PUT update user
router.put('/users/:id', (req, res) => {
    const index = users.findIndex(u => u.id === Number(req.params.id));

    if (index === -1) {
        return res.status(404).json({ error: 'User not found' });
    }

    const { name, email } = req.body;
    users[index] = {
        ...users[index],
        ...(name && { name }),
        ...(email && { email }),
        updatedAt: new Date().toISOString()
    };

    res.json(users[index]);
});

// DELETE user
router.delete('/users/:id', (req, res) => {
    const index = users.findIndex(u => u.id === Number(req.params.id));

    if (index === -1) {
        return res.status(404).json({ error: 'User not found' });
    }

    users.splice(index, 1);
    res.status(204).send();
});

// ============ Items API ============

// GET all items
router.get('/items', (req, res) => {
    const { limit = 10, offset = 0, minPrice, maxPrice } = req.query;

    let filteredItems = [...items];

    if (minPrice) {
        filteredItems = filteredItems.filter(i => i.price >= Number(minPrice));
    }
    if (maxPrice) {
        filteredItems = filteredItems.filter(i => i.price <= Number(maxPrice));
    }

    const paginatedItems = filteredItems.slice(Number(offset), Number(offset) + Number(limit));

    res.json({
        data: paginatedItems,
        total: filteredItems.length,
        limit: Number(limit),
        offset: Number(offset)
    });
});

// GET item by ID
router.get('/items/:id', (req, res) => {
    const item = items.find(i => i.id === Number(req.params.id));

    if (!item) {
        return res.status(404).json({ error: 'Item not found' });
    }

    res.json(item);
});

// POST create item
router.post('/items', (req, res) => {
    const { name, description, price } = req.body;

    if (!name || price === undefined) {
        return res.status(400).json({ error: 'Name and price are required' });
    }

    const newItem = {
        id: nextItemId++,
        name,
        description: description || '',
        price: Number(price),
        createdAt: new Date().toISOString()
    };

    items.push(newItem);
    res.status(201).json(newItem);
});

// PUT update item
router.put('/items/:id', (req, res) => {
    const index = items.findIndex(i => i.id === Number(req.params.id));

    if (index === -1) {
        return res.status(404).json({ error: 'Item not found' });
    }

    const { name, description, price } = req.body;
    items[index] = {
        ...items[index],
        ...(name && { name }),
        ...(description !== undefined && { description }),
        ...(price !== undefined && { price: Number(price) }),
        updatedAt: new Date().toISOString()
    };

    res.json(items[index]);
});

// DELETE item
router.delete('/items/:id', (req, res) => {
    const index = items.findIndex(i => i.id === Number(req.params.id));

    if (index === -1) {
        return res.status(404).json({ error: 'Item not found' });
    }

    items.splice(index, 1);
    res.status(204).send();
});

module.exports = router;
    """.trimIndent()

    val PACKAGE_JSON = """
{
  "name": "express-api",
  "version": "1.0.0",
  "description": "Express.js REST API",
  "main": "index.js",
  "scripts": {
    "start": "node index.js",
    "dev": "nodemon index.js",
    "test": "echo \"Error: no test specified\" && exit 1"
  },
  "keywords": ["api", "express", "rest"],
  "author": "",
  "license": "MIT",
  "dependencies": {
    "cors": "^2.8.5",
    "dotenv": "^16.3.1",
    "express": "^4.18.2",
    "morgan": "^1.10.0"
  },
  "devDependencies": {
    "nodemon": "^3.0.2"
  }
}
    """.trimIndent()

    val ENV_EXAMPLE = """
# Server Configuration
PORT=3000
NODE_ENV=development

# Database (example for future use)
# DATABASE_URL=postgresql://user:password@localhost:5432/mydb

# JWT Secret (example for future use)
# JWT_SECRET=your-super-secret-key

# API Keys (example for future use)
# API_KEY=your-api-key
    """.trimIndent()
}
