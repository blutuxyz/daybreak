// A local stand-in for OpenRouter, for trying the assistant without a key or costs (npm run dev starts it
// when OPENROUTER_API_KEY is not set). It answers with scripted streams: tool calls for some questions
// (exams, what to study, writing a message), then a text that quotes the tool results, so the whole loop
// server -> app tools -> server can be seen. The model id "broken/model" fails like a wrong id does.
// It never sends anything anywhere; a drafted message is only sent if the student taps Send in the app.
import http from 'node:http';

function sse(res, chunks) {
  res.writeHead(200, { 'content-type': 'text/event-stream' });
  let i = 0;
  const tick = () => {
    if (i < chunks.length) {
      res.write(`data: ${JSON.stringify(chunks[i++])}\n\n`);
      setTimeout(tick, 40);
    } else {
      res.end('data: [DONE]\n\n');
    }
  };
  setTimeout(tick, 400);
}

function textChunks(text) {
  const words = text.split(/(?<= )/);
  return [
    ...words.map((w) => ({ choices: [{ delta: { content: w } }] })),
    { choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 1200, completion_tokens: 60, cost: 0.0004 }, provider: 'FakeProvider', model: 'fake' },
  ];
}

function toolChunks(name, args) {
  return [
    { choices: [{ delta: { tool_calls: [{ index: 0, id: `call_${Date.now()}`, type: 'function', function: { name, arguments: JSON.stringify(args) } }] } }] },
    { choices: [{ delta: {}, finish_reason: 'tool_calls' }], usage: { prompt_tokens: 1100, completion_tokens: 20, cost: 0.0003 }, provider: 'FakeProvider', model: 'fake' },
  ];
}

export function startFakeOpenRouter(port = 4000) {
  return http
  .createServer((req, res) => {
    if (req.url.endsWith('/models')) {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ data: [{ id: 'anthropic/claude-sonnet-5.5' }, { id: 'anthropic/claude-haiku-5.5' }, { id: 'anthropic/claude-opus-5.5' }] }));
      return;
    }
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => {
      const request = JSON.parse(body || '{}');
      if (request.model === 'broken/model') {
        res.writeHead(400, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ error: { message: 'broken/model is not a valid model ID' } }));
        return;
      }
      // Chat titles are asked without streaming: answer with a short title from the question.
      if (request.stream !== true) {
        const asked = String(request.messages?.at(-1)?.content ?? '').match(/Question: (.*)/)?.[1] ?? 'Chat';
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ choices: [{ message: { content: `Test: ${asked.split(/\s+/).slice(0, 3).join(' ')}` } }], usage: { cost: 0.00005 } }));
        return;
      }
      const messages = request.messages ?? [];
      const last = messages.at(-1);
      const firstUser = [...messages].reverse().find((m) => m.role === 'user');
      const question = typeof firstUser?.content === 'string' ? firstUser.content : (firstUser?.content ?? []).map((p) => p.text ?? `[${p.type}]`).join(' ');
      const toolResults = messages.filter((m) => m.role === 'tool').map((m) => m.content);
      const hasImage = Array.isArray(firstUser?.content) && firstUser.content.some((p) => p.type === 'image_url');
      const lang = JSON.stringify(request.messages?.[0]?.content ?? '').includes('German') ? 'de' : 'en';

      if (last.role === 'user') {
        if (hasImage) return sse(res, textChunks(lang === 'de' ? `Ich sehe ein Bild (${Math.round(firstUser.content.find((p) => p.type === 'image_url').image_url.url.length / 1024)} KB). Auf dem Foto steht ein Arbeitsblatt – frag mich gern etwas dazu.` : 'I can see a picture. Ask me anything about it.'));
        if (/löse|rechne|solve|gleichung/i.test(question)) {
          return sse(res, textChunks(String.raw`(Testantwort) Für $x^2 - 5x + 6 = 0$ nimmst du die Mitternachtsformel:

$$x_{1,2} = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} = \frac{5 \pm \sqrt{25 - 24}}{2}$$

Also **$x_1 = 3$** und **$x_2 = 2$**.`));
        }
        if (/prüfung|exam/i.test(question)) return sse(res, toolChunks('get_exams', {}));
        if (/lernen|study/i.test(question)) return sse(res, toolChunks('get_exams', {}));
        if (/nachricht|message|schreib/i.test(question)) return sse(res, toolChunks('list_teachers', {}));
        if (/morgen|tomorrow/i.test(question)) {
          const ctx = JSON.stringify(request.messages[0].content);
          return sse(res, textChunks(lang === 'de' ? `(Testantwort) Ich habe deinen Stundenplan bekommen: ${ctx.length} Zeichen Zusammenfassung. **Das ist eine lokale Test-Antwort**, kein echtes Modell.` : `(Test answer) I received your timetable summary (${ctx.length} chars). **This is a local test answer**, not a real model.`));
        }
        return sse(res, textChunks(lang === 'de' ? 'Hallo! Das ist eine lokale Test-Antwort ohne echtes KI-Modell.' : 'Hi! This is a local test answer without a real AI model.'));
      }
      // After tool results
      const result = toolResults.at(-1) ?? '';
      if (/^Teachers the student may write to/.test(result)) {
        const match = /user_id (\d+): ([^\n(–]+)/.exec(result);
        if (match) {
          return sse(res, toolChunks('draft_message', {
            recipient_user_id: Number(match[1]),
            subject: 'TEST – bitte nicht senden',
            body: 'Dies ist ein Testentwurf aus dem lokalen Test. Er darf nicht gesendet werden.',
          }));
        }
      }
      if (/^(cancelled|sent|not sent)/.test(result)) {
        return sse(res, textChunks(lang === 'de' ? `Ergebnis vom Gerät: „${result.slice(0, 120)}“` : `Result from the device: "${result.slice(0, 120)}"`));
      }
      if (/^Exams:|^No exams/.test(result) && /lernen|study/i.test(question) && toolResults.length === 1) {
        const subject = /\d{2}:\d{2}-\d{2}:\d{2} (\S+)/.exec(result)?.[1] ?? 'M';
        return sse(res, toolChunks('get_lesson_notes', { subject, weeks_back: 6 }));
      }
      const preview = result.split('\n').slice(0, 4).join(' | ').slice(0, 300);
      return sse(res, textChunks(lang === 'de' ? `Laut den Daten vom Gerät: ${preview}` : `According to the device data: ${preview}`));
    });
  })
  .listen(port, '127.0.0.1', () => console.log(`[dev] fake OpenRouter on http://127.0.0.1:${port}`));
}
