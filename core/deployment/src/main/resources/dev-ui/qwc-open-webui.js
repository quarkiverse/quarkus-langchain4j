import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/checkbox';
import '@vaadin/progress-bar';
import { envVarMappings } from 'build-time-data';

class OpenWebUI extends LitElement {

  jsonRpc = new JsonRpc(this);

  static styles = css`
  :host {
    margin: 20px;
    display: flex;
    flex-direction: column;
    align-items: left;
    justify-content: center;
    font-family: Arial, sans-serif;
  }
  div {
    background-color: #f9f9f9;
    padding: 20px;
    border-radius: 5px;
    box-shadow: 0 0 10px rgba(0,0,0,0.1);
  }
  label {
    font-weight: bold;
  }
  .large-input {
    width: 500px;
  }
  button {
    padding: 10px 20px;
    border: none;
    border-radius: 5px;
    background-color: #007BFF;
    color: white;
    cursor: pointer;
  }
  button:hover {
    background-color: #0056b3;
  }
  button:disabled {
    background-color: #cccccc;
    cursor: not-allowed;
  }
  
  button:disabled:hover {
    background-color: #cccccc;
  }
  h2 {
    color: #333;
  }
  a {
    color: #007BFF;
    text-decoration: none;
  }
  a:hover {
    text-decoration: underline;
  }
  .hide {
      visibility: hidden;
  }
  .show {
      visibility: visible;
  }    
  `;

   static properties = {
       container: {state: true},
       image: {state: true},
       requestGpu: {state: true},
       portBindings: {state: true},
       envVars: {state: true},
       envVarMappings: {state: true},
       volumes: {state: true},
       url: {state: true},
       signedUp: {state: true},
       autoSingup: {state: true},
       username: {state: true},
       email: {state: true},
       password: {state: true},
       _progressBarClass: {state: true},
   }

   constructor() {
    super();
    this.image = "ghcr.io/open-webui/open-webui:main";
    this.requestGpu = false;
    this.portBindings = {3000 : 8080};
    this.envVars = {};
    this.envVarMappings = envVarMappings;
    this.volumes = {"open-webui": "/app/backend/data"};
    this.signedUp = false;
    this.autoSignup = true;
    this.username = "quarkus";
    this.email = "quarkus@quarkus.io";
    this.password = "quarkus";
    Object.entries(this.envVarMappings).forEach(([envVarKey, quarkusConfigPropertyKey]) => {
      this.jsonRpc.getConfigValue({key: quarkusConfigPropertyKey}).then((resp) => {
        const envVarValue = resp.result;
        if (envVarValue) {
          this.envVars[envVarKey] = envVarValue;
        }
      });
    });
    this._hideProgressBar();
    this._refresh();
    }


  render() {
    return html`<div>
      <p>
      ${this._renderContainerInfo()}
      </p>
      ${this._renderCreateOptions()}
      <vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>
      <div>
        <button @click="${this._startOpenWebUI}" ?disabled="${this.container}">Start</button>
        <button @click="${this._stopOpenWebUI}" ?disabled="${!this.container}">Stop</button>
      </div>
    </div>`;
  }


  _renderCreateOptions() {
    return html`
      <div>
        <div>
          <label>Image:</label>
          <input type="text" class="large-input" .value="${this.image}" @input="${e => this.image = e.target.value}">
        </div>
        <div>
          <label>Request GPU:</label>
          <vaadin-checkbox .checked="${this.requestGpu}" @change="${(event) => {
                                      this.requestGpu = event.target.checked;
                                      this.requestUpdate();
                                  }}"></vaadin-checkbox>
        </div>
        <details>
          <summary>Auto Signup</summary>
           <label>Enabled:</label>
           <vaadin-checkbox .checked="${this.autoSignup} @change="${(event) => {
                                      this.autoSignup = event.target.checked;
                                      this.requestUpdate();
                                  }}"></vaadin-checkbox>

           <p> 
             <label>Name:</label>
             <input type="text" .value="${this.username}" @input="${e => this.username = e.target.value}">
           </p>
           <p>
             <label>Email:</label>
             <input type="text" .value="${this.email}" @input="${e => this.email = e.target.value}">
           </p>
           <p>
             <label>Password:</label>
             <input type="text" .value="${this.password}" @input="${e => this.password = e.target.value}">
           </p>
        </details>
        <details title="Define container port bindings">
          <summary>Port Bindings</summary>
            ${Object.entries(this.portBindings).map(([key, value], index) => html`
              <div>
                <input type="text" .value="${key}" @blur="${e => this._changePortBindingKey(index, e.target.value)}">
                <input type="text" class="large-input" .value="${value}" @input="${e => this._changePortBindingValue(index, e.target.value)}">
                <button @click="${() => this._removePortBinding(key)}">-</button>
              </div>
            `)}
            <button @click="${this._addPortBinding}">+</button>
        </details>
        <details title="Define container volume bindings">
          <summary>Volumes</summary>
            ${Object.entries(this.volumes).map(([key, value], index) => html`
              <div>
                <input type="text" .value="${key}" @blur="${e => this._changeVolumeKey(index, e.target.value)}">
                <input type="text" class="large-input" .value="${value}" @input="${e => this._changeVolumeValue(index, e.target.value)}">
                <button @click="${() => this._removeVolume(key)}">-</button>
              </div>
            `)}
            <button @click="${this._addVolume}">+</button>
        </details>
        <details title="Specify environment variables as key/value pairs">
          <summary>Environment Variables</summary>
            ${Object.entries(this.envVars).map(([key, value], index) => html`
              <div>
                <input type="text" .value="${key}" @input="${e => this._changeEnvVarKey(index, e.target.value)}">
                <input type="text" class="large-input" .value="${value}" @input="${e => this._changeEnvVarValue(index, e.target.value)}">
                <button @click="${() => this._removeEnvVar(key)}">-</button>
              </div>
            `)}
            <button @click="${this._addEnvVar}">+</button>
        </details>
      </div>
    </div>`;
  }

  _refresh() {
      this.jsonRpc.inspectOpenWebUIContainer().then((resp) => {
      this.container = resp.result;
      this.requestUpdate();
    });

    this.jsonRpc.getOpenWebUIUrl().then((resp) => {
      this.url = resp.result;
      this.requestUpdate();
    });

  }
   
  _renderContainerInfo() {
      if (this.container) {
        return html`
          <div>
            <h2>Open WebUI container</h2>
            <p><label>ID:</label> ${this.container.id}</p>
            <p><label>Name:</label> ${this.container.name}</p>
            <p><label>Image:</label> ${this.container.config.image}</p>
            <p><label>Command:</label> ${this.container.config.cmd.join(" ")}</p>
            <p><label>Link:</label><a href="${this.url}" target="_blank">${this.url}</a></p>
          </div>
        `;
    }
    return html``;
  }


  _startOpenWebUI() {
    this._showProgressBar();
    this.jsonRpc.startOpenWebUI({image: this.image, requestGpu: this.requestGpu, portBindings: this.portBindings, envVars: this.envVars, volumes: this.volumes}).then(() => {  
      this._hideProgressBar();
      this._refresh();
      if (this.autoSignup) {
        // Ensure that we do have a URL before trying to sign up
        this.jsonRpc.getOpenWebUIUrl().then(() => {
          this._openWebUISignup().then(() => {
            this.signUp = true;
          });
        });
      }
    }).catch(() => {
      this._hideProgressBar();
    });
  }

  _stopOpenWebUI() {
    this._showProgressBar();
    this.jsonRpc.stopOpenWebUI().then(() => {
      this._hideProgressBar();
      this._refresh();
    }).catch(() => {
      this._hideProgressBar();
    });
  }

   _openWebUISignup(timeout=10000, interval=1000) {
    const signupUrl = this.url + "/api/v1/auths/signup";
    const payload = { name: this.username,  email: this.email, password: this.password };
    return new Promise((resolve, reject) => {
        const startTime = Date.now();
        function attemptFetch() {
          fetch(signupUrl, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload)
          }).then(response => {
              if (response.ok) {  
                  resolve('OpenWebUI ready');
              } else {
                  throw new Error('OpenWebUI not ready');
              }
          }).catch(error => {
              const currentTime = Date.now();
              if (currentTime - startTime >= timeout) {
                  reject('Timed out waiting for OpenWebUI to be ready');
              } else {
                  setTimeout(attemptFetch, interval); 
              }
          });
        }
        attemptFetch();
      }); 
    }

  // Port Bindings
  _changePortBindingKey(index, newKey) {
    const oldKey = Object.keys(this.portBindings)[index];
    const value = this.portBindings[oldKey];
    delete this.portBindings[oldKey];
    this.portBindings[newKey] = value;
    this.requestUpdate();
  }
  
  _changePortBindingValue(index, newValue) {
    const key = Object.keys(this.portBindings)[index];
    this.portBindings[key] = newValue;
    this.requestUpdate();
  }
  
  _removePortBinding(key) {
    delete this.portBindings[key];
    this.requestUpdate();
  }

  _addPortBinding() {
    const max = Object.keys(this.portBindings).reduce((a, b) => Math.max(a, b), 0);
    const next = max + 1;
    this.portBindings[next] = "";
    this.requestUpdate();
  }


  // Env Vars 
  _changeEnvVarKey(index, newKey) {
    const oldKey = Object.keys(this.envVars)[index];
    const value = this.envVars[oldKey];
    delete this.envVars[oldKey];
    this.envVars[newKey] = value;
    this.requestUpdate();
  }
  
  _changeEnvVarValue(index, newValue) {
    const key = Object.keys(this.envVars)[index];
    this.envVars[key] = newValue;
    this.requestUpdate();
  }
  
  _removeEnvVar(key) {
    delete this.envVars[key];
    this.requestUpdate();
  }

  _addEnvVar() {
    this.envVars[""] = "";
    this.requestUpdate();
  }

  // Volumes
  _changeVolumeKey(index, newKey) {
    const oldKey = Object.keys(this.volumes)[index];
    const value = this.volumes[oldKey];
    delete this.volumes[oldKey];
    this.volumes[newKey] = value;
    this.requestUpdate();
  }
  
  _changeVolumeValue(index, newValue) {
      const key = Object.keys(this.volumes)[index];
      this.volumes[key] = newValue;
      this.requestUpdate();
  }
  
  _removeVolume(key) {
    delete this.volumes[key];
    this.requestUpdate();
  }
  
  _addVolume() {
      this.volumes[""] = "";
      this.requestUpdate();
  }

  _hideProgressBar(){
    this._progressBarClass = "hide";
  }

   _showProgressBar(){
     this._progressBarClass = "show";
   }
}
customElements.define('qwc-open-webui', OpenWebUI);





