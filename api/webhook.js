const express = require('express');
const axios = require('axios');
const Groq = require('groq-sdk');  // Import Groq SDK
const app = express();
app.use(express.json());

// Initialize Groq with your API Key
const groq = new Groq({ apiKey: process.env.GROK_API_KEY });

// Load environment variables
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN;
const VERIFY_TOKEN = process.env.VERIFY_TOKEN;

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
app.post('/api/webhook', async (req, res) => { 
  if (!req.body.entry || !req.body.entry[0].messaging) {
    return res.sendStatus(400);
  }

  const messaging = req.body.entry[0].messaging[0];
  const messageText = messaging.message?.text;
  const senderId = messaging.sender.id;

  if (!messageText) {
    return res.sendStatus(200); // Ignore non-text messages
  }

  try {
    // Call Groq API using the new SDK method
    const completion = await groq.chat.completions.create({
      messages: [
        {
          role: "user",
          content: messageText,  // Send the incoming message as input
        },
      ],
      model: "llama-3.3-70b-versatile",  // You can change the model if needed
    });

    const reply = completion.choices[0].message.content || "Sorry, I couldn't understand that.";

    // Send reply to Facebook
    await axios.post(`https://graph.facebook.com/v19.0/me/messages?access_token=${PAGE_ACCESS_TOKEN}`, {
      recipient: { id: senderId },
      message: { text: reply }
    });

  } catch (error) {
    console.error('Error:', error.response?.data || error.message);
  }

  res.sendStatus(200);
});

// Start server (Vercel handles this automatically)
module.exports = app;
