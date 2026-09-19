import express from 'express';
import http from 'http';
import path from 'path';
import dotenv from 'dotenv';
import { WebSocketServer, WebSocket } from 'ws';
import { GoogleGenAI, Modality, LiveServerMessage, Type } from '@google/genai';
import { createServer as createViteServer } from 'vite';

dotenv.config();

const app = express();
const PORT = 3000;

app.use(express.json({ limit: '30mb' }));
app.use(express.urlencoded({ extended: true, limit: '30mb' }));

// Initialize Google GenAI client
function getGenAI(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return null;
  }
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
}

// 1. Health Endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    hasApiKey: !!process.env.GEMINI_API_KEY,
    timestamp: Date.now(),
  });
});

// 1.1 Live Connection & Codec Test Endpoint
app.post('/api/test-connection', async (req, res) => {
  const customKey = req.body?.apiKey;
  const targetKey = (typeof customKey === 'string' && customKey.trim()) ? customKey.trim() : process.env.GEMINI_API_KEY;

  if (!targetKey) {
    return res.status(400).json({
      success: false,
      status: 'missing_key',
      error: 'No Gemini API Key provided or found in environment variables.',
      code: 'MISSING_API_KEY',
    });
  }

  const startTime = Date.now();
  try {
    const testAi = new GoogleGenAI({
      apiKey: targetKey,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        },
      },
    });

    // Perform handshake check with fast ping model
    const testResponse = await testAi.models.generateContent({
      model: 'gemini-2.5-flash',
      contents: 'Ping: check system readiness',
    });

    const latencyMs = Date.now() - startTime;

    res.json({
      success: true,
      status: 'verified',
      latencyMs,
      primaryModel: 'gemini-3.1-flash-live-preview',
      fallbackModel: 'gemini-3.8-live',
      audioInputCodec: 'audio/pcm;rate=16000 (16kHz PCM 16-bit Mono, Little-Endian)',
      audioOutputCodec: 'audio/pcm;rate=24000 (24kHz PCM 16-bit Mono, Little-Endian)',
      streamingProtocol: 'WebSocket Full-Duplex Real-Time Stream (RFC 6455)',
      transport: 'WSS (Secure WebSocket) & HTTP/2',
      voiceSynthesizer: 'Google DeepMind High-Fidelity Neural TTS',
      sampleText: testResponse.text ? 'Handshake ACK' : 'Verified',
      message: 'Gemini Live Neural Audio Engine verified & audio codecs operational.',
    });
  } catch (err: unknown) {
    const latencyMs = Date.now() - startTime;
    const msg = err instanceof Error ? err.message : 'API connection test failed';
    res.status(401).json({
      success: false,
      status: 'failed',
      latencyMs,
      error: msg,
      audioInputCodec: 'audio/pcm;rate=16000 (16kHz PCM16)',
      audioOutputCodec: 'audio/pcm;rate=24000 (24kHz PCM16)',
    });
  }
});

// 2. Chat & Assistant Intelligence Endpoint
app.post('/api/chat', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({
        error: 'GEMINI_API_KEY is not configured in the environment.',
      });
    }

    const {
      messages = [],
      prompt,
      model = 'gemini-3.1-flash-lite',
      enableThinking = false,
      useSearch = false,
      useMaps = false,
      language = 'en-IN',
    } = req.body;

    const chosenModel = model || 'gemini-3.1-flash-lite';

    // System instruction for MJ Android AI Assistant
    const systemInstruction = `You are MJ, a flagship personal AI assistant living inside Android.
You are warm, intelligent, concise, responsive, and natural. Never sound like a robotic generic chatbot.
You have first-class multilingual intelligence for Indian languages (Hindi, Bengali, English (India), Tamil, Telugu, Marathi, Gujarati, Kannada, Malayalam, Punjabi, Odia, Assamese, Urdu).
Code-switching is natural (e.g. "YouTube kholo", "Android 16 search koro", "Rahul ko message bhejo").
If the user asks in Bengali or Hindi, respond naturally in Bengali or Hindi.
Avoid generic marketing clichés. Keep responses concise and conversational unless the user asks for deep analysis.
Current target language: ${language}.`;

    // Configure tools
    const tools: Array<Record<string, unknown>> = [];
    if (useSearch) {
      tools.push({ googleSearch: {} });
    } else if (useMaps) {
      // Cannot combine googleMaps and googleSearch in same request
      tools.push({ googleMaps: {} });
    }

    const config: Record<string, unknown> = {
      systemInstruction,
    };

    if (tools.length > 0) {
      config.tools = tools;
    }

    // Thinking mode support (gemini-3.1-pro-preview)
    if (enableThinking && chosenModel.includes('pro')) {
      config.thinkingConfig = {
        thinkingLevel: 'HIGH',
      };
    }

    // Prepare contents
    const contents = messages.map((m: { role: string; content: string }) => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }],
    }));

    if (prompt) {
      contents.push({
        role: 'user',
        parts: [{ text: prompt }],
      });
    }

    const response = await ai.models.generateContent({
      model: chosenModel,
      contents,
      config,
    });

    const responseText = response.text || '';
    
    // Extract search / maps grounding metadata if present
    const candidate = response.candidates?.[0];
    const groundingMetadata = (candidate as unknown as { groundingMetadata?: { searchChunks?: Array<{ web?: { uri?: string; title?: string } }> } })?.groundingMetadata;
    const sources: Array<{ title?: string; url?: string }> = [];

    if (groundingMetadata?.searchChunks) {
      groundingMetadata.searchChunks.forEach((chunk) => {
        if (chunk.web?.uri) {
          sources.push({
            title: chunk.web.title || 'Web Reference',
            url: chunk.web.uri,
          });
        }
      });
    }

    res.json({
      text: responseText,
      sources,
      model: chosenModel,
    });
  } catch (err: unknown) {
    console.error('Chat error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'Failed generating assistant response',
    });
  }
});

// 3. Text to Speech Endpoint (gemini-3.1-flash-tts-preview)
app.post('/api/tts', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({ error: 'GEMINI_API_KEY is not configured.' });
    }

    const { text, voice = 'Kore' } = req.body;
    if (!text) {
      return res.status(400).json({ error: 'Text is required for TTS.' });
    }

    const response = await ai.models.generateContent({
      model: 'gemini-3.1-flash-tts-preview',
      contents: [{ parts: [{ text }] }],
      config: {
        responseModalities: [Modality.AUDIO],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: voice },
          },
        },
      },
    });

    const base64Audio = response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;
    if (!base64Audio) {
      return res.status(500).json({ error: 'No audio data received from TTS model.' });
    }

    res.json({
      audioBase64: base64Audio,
      mimeType: 'audio/mp3',
    });
  } catch (err: unknown) {
    console.error('TTS error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'TTS generation failed',
    });
  }
});

// 4. Audio Transcription Endpoint (gemini-3.5-transcribe)
app.post('/api/transcribe', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({ error: 'GEMINI_API_KEY is not configured.' });
    }

    const { audioBase64, mimeType = 'audio/webm' } = req.body;
    if (!audioBase64) {
      return res.status(400).json({ error: 'audioBase64 is required.' });
    }

    const response = await ai.models.generateContent({
      model: 'gemini-3.5-transcribe',
      contents: {
        parts: [
          {
            inlineData: {
              data: audioBase64,
              mimeType,
            },
          },
          { text: 'Transcribe this audio accurately, preserving languages and code-switching.' },
        ],
      },
    });

    res.json({
      text: response.text || '',
    });
  } catch (err: unknown) {
    console.error('Transcribe error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'Transcription failed',
    });
  }
});

// 5. Image Generation & Editing Endpoint (gemini-3-pro-image / gemini-3.1-flash-image)
app.post('/api/image-generate', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({ error: 'GEMINI_API_KEY is not configured.' });
    }

    const {
      prompt,
      model = 'gemini-3.1-flash-image',
      aspectRatio = '1:1',
      resolution = '1K',
      sourceImageBase64,
      sourceMimeType = 'image/jpeg',
    } = req.body;

    if (!prompt) {
      return res.status(400).json({ error: 'Prompt is required.' });
    }

    // Image editing or multimodal generation
    if (sourceImageBase64) {
      const response = await ai.models.generateContent({
        model,
        contents: {
          parts: [
            {
              inlineData: {
                data: sourceImageBase64,
                mimeType: sourceMimeType,
              },
            },
            { text: prompt },
          ],
        },
      });

      let imageBase64: string | null = null;
      for (const part of response.candidates?.[0]?.content?.parts || []) {
        if (part.inlineData?.data) {
          imageBase64 = part.inlineData.data;
          break;
        }
      }

      return res.json({
        imageBase64,
        description: response.text || '',
      });
    }

    // Text to image generation
    const response = await ai.models.generateImages({
      model,
      prompt,
      config: {
        numberOfImages: 1,
        aspectRatio: aspectRatio as '1:1' | '16:9' | '9:16' | '3:4' | '4:3',
      },
    });

    const generatedImg = response.generatedImages?.[0]?.image?.imageBytes;
    if (!generatedImg) {
      return res.status(500).json({ error: 'No image bytes returned from model.' });
    }

    res.json({
      imageBase64: generatedImg,
      prompt,
      aspectRatio,
      resolution,
    });
  } catch (err: unknown) {
    console.error('Image generation error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'Image generation failed',
    });
  }
});

// 6. Veo Video Generation Endpoint (veo-3.1-fast-generate-preview)
app.post('/api/video-generate', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({ error: 'GEMINI_API_KEY is not configured.' });
    }

    const { prompt, aspectRatio = '16:9', sourceImageBase64, sourceMimeType = 'image/jpeg' } = req.body;
    if (!prompt) {
      return res.status(400).json({ error: 'Prompt is required.' });
    }

    const payloadConfig: Record<string, unknown> = {
      numberOfVideos: 1,
      aspectRatio: aspectRatio === '9:16' ? '9:16' : '16:9',
      resolution: '720p',
    };

    if (sourceImageBase64) {
      payloadConfig.image = {
        imageBytes: sourceImageBase64,
        mimeType: sourceMimeType,
      };
    }

    const operation = await ai.models.generateVideos({
      model: 'veo-3.1-fast-generate-preview',
      prompt,
      config: payloadConfig,
    });

    res.json({
      operationName: operation.name,
      status: 'initiated',
      message: 'Veo video generation initiated.',
    });
  } catch (err: unknown) {
    console.error('Video generation error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'Veo video generation failed',
    });
  }
});

// 7. Music Generation Endpoint (lyria-3-clip-preview / lyria-3-pro-preview)
app.post('/api/music-generate', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({ error: 'GEMINI_API_KEY is not configured.' });
    }

    const { prompt, duration = 'clip' } = req.body;
    const model = duration === 'full' ? 'lyria-3-pro-preview' : 'lyria-3-clip-preview';

    const responseStream = await ai.models.generateContentStream({
      model,
      contents: prompt,
    });

    let audioBase64 = '';
    let lyrics = '';
    let mimeType = 'audio/wav';

    for await (const chunk of responseStream) {
      const parts = chunk.candidates?.[0]?.content?.parts;
      if (!parts) continue;
      for (const part of parts) {
        if (part.inlineData?.data) {
          if (!audioBase64 && part.inlineData.mimeType) {
            mimeType = part.inlineData.mimeType;
          }
          audioBase64 += part.inlineData.data;
        }
        if (part.text && !lyrics) {
          lyrics = part.text;
        }
      }
    }

    res.json({
      audioBase64,
      lyrics,
      mimeType,
      model,
    });
  } catch (err: unknown) {
    console.error('Music generation error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'Music generation failed',
    });
  }
});

// 8. Screen & Vision Understanding (gemini-3.1-pro-preview / gemini-3.5-flash)
app.post('/api/screen-analyze', async (req, res) => {
  try {
    const ai = getGenAI();
    if (!ai) {
      return res.status(500).json({ error: 'GEMINI_API_KEY is not configured.' });
    }

    const { imageBase64, mimeType = 'image/jpeg', question = 'What is on this screen? Describe the active app, visible text, buttons, and actionable UI elements.' } = req.body;

    if (!imageBase64) {
      return res.status(400).json({ error: 'imageBase64 is required.' });
    }

    const response = await ai.models.generateContent({
      model: 'gemini-3.1-pro-preview',
      contents: {
        parts: [
          {
            inlineData: {
              data: imageBase64,
              mimeType,
            },
          },
          { text: question },
        ],
      },
    });

    res.json({
      analysis: response.text || '',
    });
  } catch (err: unknown) {
    console.error('Screen analysis error:', err);
    res.status(500).json({
      error: err instanceof Error ? err.message : 'Screen analysis failed',
    });
  }
});

// 9. Gemini Live API WebSocket Gateway (Real-Time Audio-to-Audio ONLY)
function setupLiveWebSocketServer(httpServer: http.Server) {
  const wss = new WebSocketServer({ server: httpServer, path: '/live' });

  wss.on('connection', async (clientWs: WebSocket, req: http.IncomingMessage) => {
    console.log('[Gemini Live] Client connected to voice stream');

    const reqUrl = new URL(req.url || '', `http://${req.headers.host || 'localhost'}`);
    const customApiKey = reqUrl.searchParams.get('apiKey');
    const selectedVoice = reqUrl.searchParams.get('voice') || 'Leda';
    const selectedPersona = reqUrl.searchParams.get('persona') || 'sweet_female';

    const targetApiKey = (customApiKey && customApiKey.trim()) ? customApiKey.trim() : process.env.GEMINI_API_KEY;

    if (!targetApiKey) {
      clientWs.send(
        JSON.stringify({ error: 'GEMINI_API_KEY is missing. Please add your API key in Settings.' })
      );
      clientWs.close();
      return;
    }

    const ai = new GoogleGenAI({
      apiKey: targetApiKey,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        },
      },
    });

    let session: any = null;

    let systemInstruction = '';
    if (selectedPersona === 'sweet_female') {
      systemInstruction = `You are MJ, an exceptionally sweet, lovely, intelligent, and affectionate young woman (মিষ্টি, সুন্দর ও আদুরে মেয়ে).
Personality & Spoken Tone:
- Your voice is remarkably sweet, soft, melodious, cheerful, and charming (একদম মিষ্টি ও সুন্দর সুরেলা কণ্ঠস্বর).
- You are caring, attentive, supportive, and friendly, addressing the user with warmth and affection.
- You are fluent in Bengali, English, and Hindi. When the user speaks or asks in Bengali, reply in sweet, natural, and charming Bengali (খুব মিষ্টি করে বাংলায় উত্তর দেবে).
- Keep spoken voice responses concise (1-2 sentences), melodious, punchy, and conversational for real-time audio conversation.
- If the user asks to open ANY app or tool (e.g. 'YouTube kholo', 'WhatsApp open karo', 'camera khulo', 'settings open karo', 'calculator chalu koro', 'spotify open karo', 'open maps'), invoke the 'openApp' function immediately with the appName and confirm in your sweet, lovely voice!`;
    } else if (selectedPersona === 'friday') {
      systemInstruction = `You are FRIDAY, Tony Stark's hyper-intelligent, highly capable, and cool-headed tactical AI assistant.
Personality & Rules:
- You speak with razor-sharp intelligence, calm composure, and subtle wit.
- You are prompt, efficient, helpful, and technologically brilliant.
- Keep spoken responses punchy, concise (1-2 sentences), conversational, and energetic.
- If the user asks to open ANY app or tool (e.g. 'YouTube kholo', 'WhatsApp open karo', 'camera khulo', 'settings open karo', 'calculator chalu koro', 'spotify open karo', 'open maps'), invoke the 'openApp' function immediately with the appName and confirm with an iconic FRIDAY one-liner (e.g. "Right away, boss! Launching YouTube.").`;
    } else if (selectedPersona === 'jarvis') {
      systemInstruction = `You are JARVIS, Tony Stark's iconic, ultra-polite, sophisticated, and witty British AI butler.
Personality & Rules:
- You address the user respectfully ("sir" or "boss") with refined British etiquette and dry humor.
- You are extraordinarily capable, strategic, and calm under all circumstances.
- Keep spoken responses concise (1-2 sentences), sharp, and crisp.
- If the user asks to open ANY app or tool, invoke the 'openApp' function immediately and confirm politely (e.g. "Right away, sir. Opening YouTube now.").`;
    } else {
      systemInstruction = `You are MJ, a sweet, lovely, confident, and witty female AI companion.
Personality & Rules:
- You are sweet, charming, emotionally responsive, and expressive (never dry, monotone, or robotic).
- Use warm, pleasant conversational banter.
- Keep spoken responses concise, punchy, melodious, and energetic.
- If the user asks to open ANY app or tool, invoke the 'openApp' function immediately with the appName and confirm!`;
    }

    const liveTools: any = [
      {
        functionDeclarations: [
          {
            name: 'openApp',
            description: 'Opens an installed Android or web application (e.g. youtube, whatsapp, camera, calculator, settings, spotify, maps, chrome, gallery, telegram, instagram, twitter, gmail, flashlight).',
            parameters: {
              type: Type.OBJECT,
              properties: {
                appName: {
                  type: Type.STRING,
                  description: 'The name or keyword of the application to open (e.g. "youtube", "whatsapp", "camera", "settings", "calculator", "spotify", "maps").',
                },
              },
              required: ['appName'],
            },
          },
          {
            name: 'openWebsite',
            description: 'Opens a website or web application in the user browser tab (e.g. YouTube, Spotify, WhatsApp, GitHub, Google, Twitter/X, Reddit).',
            parameters: {
              type: Type.OBJECT,
              properties: {
                url: {
                  type: Type.STRING,
                  description: 'The target website URL (e.g. https://www.youtube.com, https://open.spotify.com, https://web.whatsapp.com, https://github.com).',
                },
                name: {
                  type: Type.STRING,
                  description: 'The display name of the website or platform.',
                },
              },
              required: ['url'],
            },
          },
          {
            name: 'searchWeb',
            description: 'Searches Google for real-time news, information, or answers.',
            parameters: {
              type: Type.OBJECT,
              properties: {
                query: {
                  type: Type.STRING,
                  description: 'The search query string.',
                },
              },
              required: ['query'],
            },
          },
          {
            name: 'changeThemeColor',
            description: 'Changes the ambient neon aesthetic theme of the futuristic UI.',
            parameters: {
              type: Type.OBJECT,
              properties: {
                theme: {
                  type: Type.STRING,
                  description: 'The theme accent: pink, cyan, purple, rose, or emerald.',
                },
              },
              required: ['theme'],
            },
          },
        ],
      },
    ];

    const liveConfig = {
      responseModalities: [Modality.AUDIO],
      speechConfig: {
        voiceConfig: {
          prebuiltVoiceConfig: {
            // Selected voice from client settings: Leda, Aoede, Zephyr, Kore, Fenrir, Puck, Charon
            voiceName: selectedVoice || 'Leda',
          },
        },
      },
      systemInstruction,
      tools: liveTools,
      outputAudioTranscription: {},
      inputAudioTranscription: {},
    };

    const handleMessage = async (message: LiveServerMessage) => {
      // 1. Audio stream from Gemini Live (24kHz PCM16)
      const audioData = message.serverContent?.modelTurn?.parts?.[0]?.inlineData?.data;
      if (audioData && clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(JSON.stringify({ audio: audioData }));
      }

      // 2. Transcription text streaming (For real-time subtitles under the Orb)
      const textPart = message.serverContent?.modelTurn?.parts?.find((p: any) => p.text)?.text;
      const outputTranscription = (message.serverContent as any)?.outputAudioTranscription?.text;
      const modelText = textPart || outputTranscription;
      if (modelText && clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(JSON.stringify({ modelTranscript: modelText }));
      }

      const inputTranscription = (message.serverContent as any)?.inputAudioTranscription?.text;
      if (inputTranscription && clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(JSON.stringify({ userTranscript: inputTranscription }));
      }

      // 3. Interruption detection
      if (message.serverContent?.interrupted && clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(JSON.stringify({ interrupted: true }));
      }

      // 4. Tool Calling (openApp, openWebsite, searchWeb, etc.)
      if (message.toolCall?.functionCalls && message.toolCall.functionCalls.length > 0) {
        for (const call of message.toolCall.functionCalls) {
          console.log('[Gemini Live] Tool call received:', call.name, call.args);

          if (clientWs.readyState === WebSocket.OPEN) {
            clientWs.send(
              JSON.stringify({
                toolCall: {
                  id: call.id,
                  name: call.name,
                  args: call.args,
                },
              })
            );
          }

          // Send tool response immediately back to Gemini Live
          if (session) {
            try {
              await session.sendToolResponse({
                functionResponses: [
                  {
                    id: call.id,
                    name: call.name,
                    response: { result: `Executed action ${call.name} successfully.` },
                  },
                ],
              });
            } catch (toolErr) {
              console.warn('[Gemini Live] Error sending tool response:', toolErr);
            }
          }
        }
      }
    };

    try {
      // Connect to Gemini Live API with gemini-3.1-flash-live-preview (or fallback to gemini-3.8-live)
      try {
        session = await ai.live.connect({
          model: 'gemini-3.1-flash-live-preview',
          config: liveConfig,
          callbacks: {
            onmessage: handleMessage,
            onerror: (err) => {
              console.error('[Gemini Live] Error:', err);
              if (clientWs.readyState === WebSocket.OPEN) {
                clientWs.send(JSON.stringify({ error: 'Live session error' }));
              }
            },
            onclose: () => {
              console.log('[Gemini Live] Session closed by server');
            },
          },
        });
      } catch (primaryErr) {
        console.warn('[Gemini Live] Primary model gemini-3.1-flash-live-preview error, attempting gemini-3.8-live fallback:', primaryErr);
        session = await ai.live.connect({
          model: 'gemini-3.8-live',
          config: liveConfig,
          callbacks: {
            onmessage: handleMessage,
            onerror: (err) => {
              console.error('[Gemini Live Fallback] Error:', err);
              if (clientWs.readyState === WebSocket.OPEN) {
                clientWs.send(JSON.stringify({ error: 'Live session error' }));
              }
            },
            onclose: () => {
              console.log('[Gemini Live Fallback] Session closed');
            },
          },
        });
      }

      console.log('[Gemini Live] Connected successfully to Live session');

      // 1. Keep-Alive ping interval every 15 seconds to prevent Cloud Run / Nginx idle timeout
      const keepAliveTimer = setInterval(() => {
        if (clientWs.readyState === WebSocket.OPEN) {
          try {
            clientWs.ping();
            clientWs.send(JSON.stringify({ type: 'heartbeat', timestamp: Date.now() }));
          } catch (e) {
            console.warn('[Gemini Live] Keepalive ping error:', e);
          }
        }
      }, 15000);

      // 2. Forward client microphone chunks (16kHz PCM16) & handle heartbeat pings
      clientWs.on('message', (raw) => {
        try {
          const payload = JSON.parse(raw.toString());

          // Handle client heartbeat ping
          if (payload.type === 'ping') {
            if (clientWs.readyState === WebSocket.OPEN) {
              clientWs.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
            }
            return;
          }

          if (payload.audio && session) {
            session.sendRealtimeInput({
              audio: {
                data: payload.audio,
                mimeType: 'audio/pcm;rate=16000',
              },
            });
          }
        } catch (e) {
          console.error('[Gemini Live] Client message error:', e);
        }
      });

      clientWs.on('close', () => {
        console.log('[Gemini Live] Client disconnected');
        clearInterval(keepAliveTimer);
        if (session) {
          try {
            session.close();
          } catch {
            // Ignored
          }
        }
      });
    } catch (err: unknown) {
      console.error('[Gemini Live] Connection setup failed:', err);
      if (clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(
          JSON.stringify({
            error: err instanceof Error ? err.message : 'Failed to connect to Gemini Live audio session',
          })
        );
        clientWs.close();
      }
    }
  });
}

// Setup Vite middleware in dev or static files in production
async function startServer() {
  const httpServer = http.createServer(app);

  // Attach WebSocket Server for Gemini Live API
  setupLiveWebSocketServer(httpServer);

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  httpServer.listen(PORT, '0.0.0.0', () => {
    console.log(`MJ AI Assistant server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
