'use strict';

// Replies and server summaries are always rendered as text, including model output.
let assistantProposal = null;
const assistantTurns = [];
function renderAssistant() {
    main.innerHTML = `${heading('HPC AI Assistant', 'Ask about resources, availability, reservations, or demo policies. Review and confirm every proposed change.')}
        <p>Chat and relevant reservation details are sent to Gemini. Use non-sensitive demo data. Replies can be requested in English, Vietnamese, or Spanish.</p>
        <div id="assistant-transcript" class="assistant-transcript" role="log" aria-live="polite"></div>
        <section id="assistant-proposal" aria-label="Review proposed change" hidden></section>
        <form id="assistant-form"><label for="assistant-message">Message</label>
        <textarea id="assistant-message" name="message" maxlength="2000" rows="3" required placeholder="Find the earliest GPU slot this week for my model training project."></textarea>
        <div class="form-actions"><button class="btn primary" type="submit">Send</button><button class="btn" type="button" id="assistant-reset">New conversation</button></div></form>`;
    assistantTurns.forEach(turn => appendAssistantTurn(turn.role, turn.text, false));
    showAssistantProposal();
}
function appendAssistantTurn(role, text, remember = true) {
    if (remember) { assistantTurns.push({ role, text }); if (assistantTurns.length > 24) assistantTurns.shift(); }
    const transcript = document.querySelector('#assistant-transcript');
    if (!transcript) return;
    const item = document.createElement('p');
    const label = document.createElement('strong'); label.textContent = `${role}: `;
    item.append(label, document.createTextNode(text)); transcript.append(item);
}
function showAssistantProposal() {
    const area = document.querySelector('#assistant-proposal');
    if (!area) return;
    area.replaceChildren(); area.hidden = !assistantProposal;
    if (!assistantProposal) return;
    const title = document.createElement('h2'); title.textContent = assistantProposal.action === 'BOOK' ? 'Review booking' : 'Review cancellation';
    const details = document.createElement('pre'); details.className = 'assistant-summary';
    const summary = assistantProposal.summary;
    const slot = summary.slot || summary;
    details.textContent = [
        assistantProposal.action === 'CANCEL' ? `Reservation: #${summary.id}` : `Slot: #${slot.slotId}`,
        `Service: ${slot.serviceName}`, `Provider: ${slot.providerName}`,
        `Starts: ${slot.startTime}`, `Ends: ${slot.endTime}`, 'Time zone: America/Los_Angeles',
        `Project: ${summary.projectName}`, `Requirements: ${summary.resourceRequirements || 'None'}`,
        `Email: ${summary.contactEmail}`, `Expires: ${new Date(assistantProposal.expiresAt).toLocaleTimeString()}`
    ].join('\n');
    const confirm = document.createElement('button'); confirm.className = 'btn primary'; confirm.id = 'assistant-confirm';
    confirm.textContent = assistantProposal.action === 'BOOK' ? 'Confirm booking' : 'Confirm cancellation';
    const dismiss = document.createElement('button'); dismiss.className = 'btn'; dismiss.id = 'assistant-dismiss'; dismiss.textContent = 'Dismiss';
    area.append(title, details, confirm, dismiss);
}
document.addEventListener('submit', async event => {
    if (event.target.id !== 'assistant-form') return;
    event.preventDefault();
    const form = event.target;
    if (!form.reportValidity()) return;
    const message = form.elements.message.value.trim();
    if (!message) return;
    const buttons = form.querySelectorAll('button'); buttons.forEach(b => b.disabled = true);
    assistantProposal = null; showAssistantProposal();
    appendAssistantTurn('You', message); form.elements.message.value = '';
    try {
        const reply = await api('/api/ai/chat', { method: 'POST', body: { message } });
        appendAssistantTurn('Assistant', reply.answer);
        if (reply.sources.length) appendAssistantTurn('Policy references', reply.sources.join(', '));
        assistantProposal = reply.proposal; showAssistantProposal();
    } catch (error) { appendAssistantTurn('Status', error.message); }
    finally { buttons.forEach(b => b.disabled = false); }
});
document.addEventListener('click', async event => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.id === 'assistant-run-reminders') {
        button.disabled = true;
        try {
            const result = await api('/api/ai/reminders/run', { method: 'POST' });
            banner(`AI reminders: ${result.scanned} checked, ${result.sent} sent, ${result.failed} failed.`, result.failed === 0);
        } catch (error) { banner(error.message); }
        finally { if (button.isConnected) button.disabled = false; }
        return;
    }
    if (button.id === 'assistant-run-reminders') {
        button.disabled = true;
        try {
            const result = await api('/api/ai/reminders/run', { method: 'POST' });
            banner(`AI reminders: ${result.scanned} checked, ${result.sent} sent, ${result.failed} failed.`, result.failed === 0);
        } catch (error) { banner(error.message); }
        finally { if (button.isConnected) button.disabled = false; }
        return;
    }
    if (button.dataset.action === 'logout') { assistantTurns.length = 0; assistantProposal = null; }
    if (!['assistant-confirm', 'assistant-reset', 'assistant-dismiss'].includes(button.id)) return;
    button.disabled = true;
    try {
        if (button.id === 'assistant-confirm' && assistantProposal) {
            const token = assistantProposal.token;
            assistantProposal = null; showAssistantProposal();
            const result = await api('/api/ai/confirm', { method: 'POST', body: { token } });
            appendAssistantTurn('Scheduler', `Reservation #${result.id}: ${result.status}.`);
        } else if (button.id === 'assistant-reset') {
            await api('/api/ai/chat', { method: 'DELETE' });
            assistantTurns.length = 0; assistantProposal = null; renderAssistant();
        } else {
            // Clear the server proposal and history to ensure a dismissed token cannot be reused.
            await api('/api/ai/chat', { method: 'DELETE' });
            assistantProposal = null; assistantTurns.length = 0; renderAssistant();
        }
    } catch (error) { appendAssistantTurn('Status', error.message); }
    finally { if (button.isConnected) button.disabled = false; }
});
