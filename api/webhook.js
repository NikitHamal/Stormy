const express = require('express');
const axios = require('axios');
const app = express();
app.use(express.json());

// Load environment variables (Ensure these are stored in Vercel)
const GROK_API_KEY = process.env.GROK_API_KEY;
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN;
const VERIFY_TOKEN = process.env.VERIFY_TOKEN;

// Test route
app.get('/api/webhook', (req, res) => {
  res.send('Stormy is running!');
});

// Verify Facebook Webhook
app.get('/webhook', (req, res) => {
  if (req.query['hub.mode'] === 'subscribe' && req.query['hub.verify_token'] === VERIFY_TOKEN) {
    return res.status(200).send(req.query['hub.challenge']);
  }
  res.sendStatus(403);
});

// Handle incoming messages
app.post('/webhook', async (req, res) => {
  if (!req.body.entry || !req.body.entry[0].messaging) {
    return res.sendStatus(400);
  }

  const messaging = req.body.entry[0].messaging[0];

  if (!messaging.message || !messaging.message.text) {
    return res.sendStatus(200); // Ignore non-text messages
  }

  const messageText = messaging.message.text;
  const senderId = messaging.sender.id;

  try {
    // Call Grok API (Replace with the actual Grok API endpoint)
    const grokResponse = await axios.post('https://api.grok.com/ask', {
      prompt: messageText,
      api_key: GROK_API_KEY
    });

    const reply = grokResponse.data.reply || "Sorry, I couldn't understand that.";

    // Send reply back to Facebook
    await axios.post(`https://graph.facebook.com/v19.0/me/messages?access_token=${PAGE_ACCESS_TOKEN}`, {
      recipient: { id: senderId },
      message: { text: reply }
    });

  } catch (error) {
    console.error('Error:', error.response?.data || error.message);
  }

  res.sendStatus(200);
});

// Start Express server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});

module.exports = app;
