const express = require('express');
const axios = require('axios');
const app = express();
app.use(express.json());

// Load environment variables
const GROK_API_KEY = process.env.GROK_API_KEY;
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN;
const VERIFY_TOKEN = process.env.VERIFY_TOKEN; // Ensure this is set in Vercel

// Combined route for Facebook verification and manual testing
app.get('/api/webhook', (req, res) => {
  // Handle Facebook verification
  if (req.query['hub.mode'] === 'subscribe') {
    if (req.query['hub.verify_token'] === VERIFY_TOKEN) {
      res.status(200).send(req.query['hub.challenge']);
    } else {
      res.sendStatus(403);
    }
  } 
  // Handle manual testing (optional)
  else {
    res.send('Stormy is running! 🚀');
  }
});

// Handle incoming messages
app.post('/api/webhook', async (req, res) => { // Fix: Changed to /api/webhook
  if (!req.body.entry || !req.body.entry[0].messaging) {
    return res.sendStatus(400);
  }

  const messaging = req.body.entry[0].messaging[0];
  const messageText = messaging.message?.text;
  const senderId = messaging.sender.id;

  if (!messageText) {
    return res.sendStatus(200); // Ignore non-text messages
  }

  console.log(`Received message: ${messageText} from sender: ${senderId}`);

  try {
    // Call Grok API (replace with your endpoint)
    const grokResponse = await axios.post('https://api.grok.com/v1/chat/completions', {
      prompt: messageText,
      api_key: GROK_API_KEY
    });

    const reply = grokResponse.data.reply || "Sorry, I couldn't understand that.";
    console.log(`Grok response: ${reply}`);

    // Send reply to Facebook
    const facebookResponse = await axios.post(`https://graph.facebook.com/v19.0/me/messages?access_token=${PAGE_ACCESS_TOKEN}`, {
      recipient: { id: senderId },
      message: { text: reply }
    });

    console.log('Facebook response:', facebookResponse.data);

  } catch (error) {
    console.error('Error:', error.response?.data || error.message);
  }

  res.sendStatus(200);
});

// Start server (Vercel handles this automatically)
module.exports = app;
