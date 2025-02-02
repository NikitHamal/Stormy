const express = require('express');
const axios = require('axios');
const app = express();
app.use(express.json());

// Replace these with your actual credentials
const GROK_API_KEY = process.env.GROK_API_KEY; // Store in Vercel Environment Variables
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN; // Store in Vercel Environment Variables

app.get('/api/webhook', (req, res) => {
  res.send('Stormy is running!');
});

// Verify Facebook Webhook
app.get('/webhook', (req, res) => {
  if (req.query['hub.mode'] === 'subscribe' && req.query['hub.verify_token'] === 'YOUR_VERIFY_TOKEN') {
    res.status(200).send(req.query['hub.challenge']);
  } else {
    res.sendStatus(403);
  }
});

// Handle incoming messages
app.post('/webhook', async (req, res) => {
  const messaging = req.body.entry[0].messaging[0];
  const messageText = messaging.message.text;
  const senderId = messaging.sender.id;

  try {
    // Call Grok API
    const grokResponse = await axios.post('YOUR_GROK_API_ENDPOINT', {
      prompt: messageText,
      api_key: GROK_API_KEY
    });

    const reply = grokResponse.data.reply;

    // Send reply back to Facebook
    await axios.post(`https://graph.facebook.com/v19.0/me/messages?access_token=${PAGE_ACCESS_TOKEN}`, {
      recipient: { id: senderId },
      message: { text: reply }
    });
  } catch (error) {
    console.error('Error:', error);
  }

  res.sendStatus(200);
});

module.exports = app;
