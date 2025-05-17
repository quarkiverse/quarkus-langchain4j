import {LitElement} from 'lit';
import MarkdownIt from 'markdown-it';

export class DemoChat extends LitElement {
  #sessionId;
  #timezoneOffset;

  constructor() {
    super();
    this.md = new MarkdownIt({
      breaks: true,
      linkify: true,
      xhtmlOut: false,
      html: false,
      typographer: true,
    });
    this.#ensureSessionId();
    this.#timezoneOffset = new Date().getTimezoneOffset();
  }

  #ensureSessionId() {
    this.#sessionId = localStorage.getItem('chat_session_id');
    if (!this.#sessionId) {
      this.#sessionId = crypto.randomUUID();
      localStorage.setItem('chat_session_id', this.#sessionId);
      console.log('Created new session ID:', this.#sessionId);
    } else {
      console.log('Using existing session ID:', this.#sessionId);
    }
  }

  connectedCallback() {
    const chatBot = document.getElementsByTagName("chat-bot")[0];
    const markdown = this.md;

    // Use secure WebSocket if the page is loaded over HTTPS
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocol}//${window.location.host}/chatbot`);

    // Add connection event handlers
    socket.onopen = () => {
      console.debug('WebSocket connection established with session ID:', this.#sessionId);

      // Send initial handshake with session ID if needed
      // socket.send(JSON.stringify({ type: 'handshake', sessionId: this.#sessionId }));
    };

    socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      chatBot.sendMessage(null, {
        message: "Connection error. Please try again later.",
        right: false,
        sender: {name: 'System', id: 'system'}
      });
    };

    socket.onclose = (event) => {
      console.debug('WebSocket connection closed:', event.code, event.reason);
      // Could implement reconnection logic here if needed
    };

    socket.onmessage = function (event) {
      chatBot.hideAllLoading();
      // Render markdown from event.data
      const response = JSON.parse(event.data);
      let markdownMessage = markdown.renderInline(response.message);
      if (response.links) {
        markdownMessage += `<ul>`;
        response.links.forEach(link => {
          markdownMessage += `<li><a href="${link.url}" target="_blank">${link.title}</a></li>`;
        });
        markdownMessage += `</ul>`;
      }

      chatBot.sendMessage(null, {
        message: markdownMessage,
        right: false,
        sender: {name: 'Bob', id: '007'}
      });
    };

    chatBot.addEventListener("sent", function (e) {
      if (e.detail.message.right === true) {
        // User message
        const userMessage = e.detail.message.message.replace(/^<p>(.*)<\/p>$/, '$1');

        // Create a message object that includes the session ID
        const messagePayload = {
          message: userMessage,
          sessionId: this.#sessionId,
          timezoneOffset: this.#timezoneOffset
        };

        // Send the message as JSON
        socket.send(JSON.stringify(messagePayload));

        chatBot.sendMessage("", {
          right: false,
          sender: {name: 'Bob', id: '007'},
          loading: true
        });
      }
    }.bind(this)); // Binding this to access the sessionId
  }
}

customElements.define('demo-chat', DemoChat);
