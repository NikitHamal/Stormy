const express = require("express");
const axios = require("axios");
const bodyParser = require("body-parser");

const app = express();
app.use(bodyParser.json());

// Your Grok API Key
const GROK_API_KEY = "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"; // Replace with your actual API key

// Facebook Page Access Token
const FB_PAGE_ACCESS_TOKEN = "EAAoR3YaO7qIBO0GRjro01cCZBhs9o8KOnZC5ZCWWIb7lzcknYLrpujZB9IHkgHvPV5dpno6KShDgRDtc5aGnHipEc2bZAFru6zoTZCCvRIERv7CURPYJ6LqynzDTu1NJSd1azMCCdIjMRmJt4t5G8wrvTWFundP7XtzpqzcfMLUl80By3or7A2z0wbSitdFyf3MgZDZD"; // Replace with your actual token

// Webhook Verification
app.get("/webhook", (req, res) => {
    if (req.query["hub.verify_token"] === "iamnikithamal") {
        res.send(req.query["hub.challenge"]);
    } else {
        res.send("Invalid verification token");
    }
});

// Handle Messages
app.post("/webhook", async (req, res) => {
    const entries = req.body.entry;
    for (let entry of entries) {
        const messaging = entry.messaging;
        for (let event of messaging) {
            if (event.message && event.sender) {
                const senderId = event.sender.id;
                const userMessage = event.message.text;
                const botResponse = await getGrokResponse(userMessage);
                sendFacebookMessage(senderId, botResponse);
            }
        }
    }
    res.sendStatus(200);
});

// Function to get response from Grok API
async function getGrokResponse(userMessage) {
    try {
        const response = await axios.post("https://api.grok.x.ai/v1/generate", {
            message: userMessage,
        }, {
            headers: { Authorization: `Bearer ${GROK_API_KEY}` }
        });
        return response.data.reply;
    } catch (error) {
        return "Sorry, I am having trouble processing your request.";
    }
}

// Function to send message back to Facebook Messenger
function sendFacebookMessage(senderId, message) {
    axios.post(`https://graph.facebook.com/v12.0/me/messages?access_token=${FB_PAGE_ACCESS_TOKEN}`, {
        recipient: { id: senderId },
        message: { text: message },
    }).catch(error => console.log("Error sending message:", error));
}

// Start server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
