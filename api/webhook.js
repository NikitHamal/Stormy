const express = require('express');
const axios = require('axios');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const app = express();
app.use(express.json());

// Load environment variables
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN;
const VERIFY_TOKEN = process.env.VERIFY_TOKEN;

// Gemini API helper function
async function generateGeminiResponse(prompt) {
  try {
    const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);
    const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });
    const result = await model.generateContent(prompt);
    return result.response.text();
  } catch (error) {
    console.error('Gemini API error:', error.response?.data || error.message);
    return "Sorry, I couldn't understand that.";
  }
}

// Combined route for Facebook verification and manual testing
app.get('/api/webhook', (req, res) => {
  // Handle Facebook verification
  if (req.query['hub.mode'] === 'subscribe') {
    if (req.query['hub.verify_token'] === VERIFY_TOKEN) {
      return res.status(200).send(req.query['hub.challenge']);
    } else {
      return res.sendStatus(403);
    }
  }
  // Handle manual testing
  res.send('Stormy is running! 🚀');
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

  console.log(`Received message: ${messageText} from sender: ${senderId}`);

  try {
    // Generate response using Gemini AI
    const reply = await generateGeminiResponse(messageText);
    console.log(`Gemini reply: ${reply}`);

    // Send reply to Facebook
    const facebookResponse = await axios.post(
      `https://graph.facebook.com/v19.0/me/messages?access_token=${PAGE_ACCESS_TOKEN}`,
      {
        recipient: { id: senderId },
        message: { text: reply }
      }
    );
    console.log('Facebook response:', facebookResponse.data);
  } catch (error) {
    console.error('Error handling message:', error.response?.data || error.message);
  }

  res.sendStatus(200);
});

// Export the Express app (Vercel will handle starting the server)
module.exports = app;
