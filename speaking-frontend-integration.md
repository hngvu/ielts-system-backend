# Speaking Exam — Hướng dẫn tích hợp Frontend React

## Tổng quan

Frontend React kết nối với backend qua **WebSocket** tại:

```
ws://<server>/ws/speaking/exam?token=<JWT>
```

Toàn bộ giao tiếp trong buổi thi Speaking đều qua WebSocket — **không có REST API** nào trong flow thi.

---

## 1. Kết nối WebSocket

### Hook: `useSpeakingExam`

```tsx
// hooks/useSpeakingExam.ts
import { useRef, useState, useCallback, useEffect } from 'react';

export type ExamState =
  | 'IDLE'
  | 'CONNECTING'
  | 'PART1_QUESTIONING'
  | 'TRANSITIONING_TO_PART2'
  | 'PART2_PREPARATION'
  | 'PART2_SPEAKING'
  | 'TRANSITIONING_TO_PART3'
  | 'PART3_QUESTIONING'
  | 'EVALUATING'
  | 'COMPLETED'
  | 'ERROR';

interface QuestionEvent {
  type: 'question';
  partNumber: number;
  questionId: number;
  text: string;
  isFollowUp: boolean;
}

interface CueCardEvent {
  type: 'show_cue_card';
  duration: number;
  questionId: number;
  topic: string;
}

interface AudioChunkEvent {
  type: 'audio_chunk';
  data: string; // base64 MP3
}

interface EvaluationEvent {
  type: 'evaluation';
  final_band: number;
  fluency: number;
  vocabulary: number;
  grammar: number;
  pronunciation: number;
  feedback: {
    summary: string;
    strengths: string[];
    areas_for_improvement: string[];
    next_steps: string[];
  };
  transcript: string;
}

type ServerMessage =
  | QuestionEvent
  | CueCardEvent
  | AudioChunkEvent
  | { type: 'audio_end' }
  | { type: 'evaluating' }
  | EvaluationEvent
  | { type: 'error'; message: string };

export function useSpeakingExam(token: string) {
  const wsRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const audioChunksRef = useRef<string[]>([]);

  const [examState, setExamState] = useState<ExamState>('IDLE');
  const [currentQuestion, setCurrentQuestion] = useState<QuestionEvent | null>(null);
  const [cueCard, setCueCard] = useState<CueCardEvent | null>(null);
  const [evaluation, setEvaluation] = useState<EvaluationEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isAudioPlaying, setIsAudioPlaying] = useState(false);

  // ── Kết nối WS ───────────────────────────────────────────────────
  const connect = useCallback(() => {
    const ws = new WebSocket(
      `${import.meta.env.VITE_WS_URL}/ws/speaking/exam?token=${token}`
    );

    ws.onopen = () => {
      console.log('WS connected');
      setExamState('CONNECTING');
    };

    ws.onmessage = (event) => {
      const msg: ServerMessage = JSON.parse(event.data);
      handleServerMessage(msg);
    };

    ws.onerror = (err) => {
      console.error('WS error:', err);
      setError('Mất kết nối WebSocket');
      setExamState('ERROR');
    };

    ws.onclose = () => {
      console.log('WS closed');
    };

    wsRef.current = ws;
  }, [token]);

  // ── Xử lý message từ server ──────────────────────────────────────
  const handleServerMessage = useCallback((msg: ServerMessage) => {
    switch (msg.type) {
      case 'question':
        setCurrentQuestion(msg);
        audioChunksRef.current = []; // reset audio buffer cho câu mới
        if (msg.partNumber === 1) setExamState('PART1_QUESTIONING');
        if (msg.partNumber === 3) setExamState('PART3_QUESTIONING');
        break;

      case 'audio_chunk':
        audioChunksRef.current.push(msg.data);
        setIsAudioPlaying(true);
        break;

      case 'audio_end':
        playAudioChunks(audioChunksRef.current);
        break;

      case 'show_cue_card':
        setCueCard(msg);
        setExamState('PART2_PREPARATION');
        break;

      case 'evaluating':
        setExamState('EVALUATING');
        break;

      case 'evaluation':
        setEvaluation(msg);
        setExamState('COMPLETED');
        break;

      case 'error':
        setError(msg.message);
        setExamState('ERROR');
        break;
    }
  }, []);

  // ── Gửi message lên server ───────────────────────────────────────

  const startExam = useCallback((testId: number) => {
    wsRef.current?.send(JSON.stringify({ type: 'start_exam', testId }));
  }, []);

  const sendTranscript = useCallback((partNumber: number, questionId: number, text: string) => {
    wsRef.current?.send(JSON.stringify({
      type: 'transcript',
      partNumber,
      questionId,
      text,
    }));
  }, []);

  const startSpeakingPart2 = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'start_speaking_part2' }));
    setExamState('PART2_SPEAKING');
  }, []);

  const stopSpeakingPart2 = useCallback((text: string) => {
    wsRef.current?.send(JSON.stringify({ type: 'stop_speaking_part2', text }));
  }, []);

  const endExam = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'end_exam' }));
  }, []);

  // ── Phát audio TTS ───────────────────────────────────────────────
  const playAudioChunks = useCallback(async (chunks: string[]) => {
    if (chunks.length === 0) {
      setIsAudioPlaying(false);
      return;
    }

    try {
      // Gộp base64 chunks → 1 blob MP3
      const binaryChunks = chunks.map(b64 => {
        const binary = atob(b64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes;
      });

      const blob = new Blob(binaryChunks, { type: 'audio/mpeg' });
      const url = URL.createObjectURL(blob);
      const audio = new Audio(url);

      audio.onended = () => {
        setIsAudioPlaying(false);
        URL.revokeObjectURL(url);
      };

      await audio.play();
    } catch (err) {
      console.error('Audio playback failed:', err);
      setIsAudioPlaying(false);
    }
  }, []);

  // ── Cleanup ──────────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      wsRef.current?.close();
    };
  }, []);

  return {
    examState,
    currentQuestion,
    cueCard,
    evaluation,
    error,
    isAudioPlaying,
    connect,
    startExam,
    sendTranscript,
    startSpeakingPart2,
    stopSpeakingPart2,
    endExam,
  };
}
```

---

## 2. Speech-to-Text (AssemblyAI)

Client dùng **AssemblyAI Realtime STT** để chuyển giọng nói → text.

```tsx
// hooks/useAssemblyAISTT.ts
import { useRef, useState, useCallback } from 'react';

export function useAssemblyAISTT(apiKey: string) {
  const [transcript, setTranscript] = useState('');
  const [isListening, setIsListening] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);

  const startListening = useCallback(async () => {
    setTranscript('');

    // 1. Lấy temporary token từ AssemblyAI
    const tokenRes = await fetch('https://api.assemblyai.com/v2/realtime/token', {
      method: 'POST',
      headers: {
        'Authorization': apiKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ expires_in: 3600 }),
    });
    const { token } = await tokenRes.json();

    // 2. Kết nối WebSocket STT
    const ws = new WebSocket(
      `wss://api.assemblyai.com/v2/realtime/ws?sample_rate=16000&token=${token}`
    );

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.message_type === 'FinalTranscript' && data.text) {
        setTranscript(prev => prev + ' ' + data.text);
      }
    };

    ws.onopen = async () => {
      // 3. Capture microphone
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0 && ws.readyState === WebSocket.OPEN) {
          event.data.arrayBuffer().then(buffer => {
            ws.send(new Uint8Array(buffer));
          });
        }
      };

      mediaRecorder.start(250); // gửi mỗi 250ms
      mediaRecorderRef.current = mediaRecorder;
      setIsListening(true);
    };

    socketRef.current = ws;
  }, [apiKey]);

  const stopListening = useCallback(() => {
    mediaRecorderRef.current?.stop();
    socketRef.current?.close();
    setIsListening(false);
  }, []);

  return { transcript, isListening, startListening, stopListening };
}
```

---

## 3. Component chính: `SpeakingExamPage`

```tsx
// pages/SpeakingExamPage.tsx
import { useState, useEffect } from 'react';
import { useSpeakingExam } from '../hooks/useSpeakingExam';
import { useAssemblyAISTT } from '../hooks/useAssemblyAISTT';

interface Props {
  testId: number;
  token: string;         // JWT token
  assemblyKey: string;   // AssemblyAI API key
}

export default function SpeakingExamPage({ testId, token, assemblyKey }: Props) {
  const exam = useSpeakingExam(token);
  const stt = useAssemblyAISTT(assemblyKey);
  const [prepTimer, setPrepTimer] = useState(60);
  const [speakTimer, setSpeakTimer] = useState(120);

  // ── Kết nối + bắt đầu thi ────────────────────────────────────────
  useEffect(() => {
    exam.connect();
  }, []);

  useEffect(() => {
    if (exam.examState === 'CONNECTING') {
      exam.startExam(testId);
    }
  }, [exam.examState]);

  // ── Tự bật mic khi server hỏi câu (Part 1 & 3) ──────────────────
  useEffect(() => {
    if (exam.currentQuestion && !exam.isAudioPlaying) {
      // Đợi audio TTS phát xong rồi mới bật mic
      stt.startListening();
    }
  }, [exam.currentQuestion, exam.isAudioPlaying]);

  // ── Part 2: Đếm ngược 60s chuẩn bị ──────────────────────────────
  useEffect(() => {
    if (exam.examState !== 'PART2_PREPARATION') return;
    setPrepTimer(60);
    const interval = setInterval(() => {
      setPrepTimer(prev => {
        if (prev <= 1) {
          clearInterval(interval);
          exam.startSpeakingPart2();
          stt.startListening();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [exam.examState]);

  // ── Part 2: Đếm ngược 120s nói ──────────────────────────────────
  useEffect(() => {
    if (exam.examState !== 'PART2_SPEAKING') return;
    setSpeakTimer(120);
    const interval = setInterval(() => {
      setSpeakTimer(prev => {
        if (prev <= 1) {
          clearInterval(interval);
          handleStopPart2();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [exam.examState]);

  // ── Handlers ─────────────────────────────────────────────────────
  const handleSubmitAnswer = () => {
    if (!exam.currentQuestion) return;
    stt.stopListening();
    exam.sendTranscript(
      exam.currentQuestion.partNumber,
      exam.currentQuestion.questionId,
      stt.transcript || '[no response]'
    );
    // Server sẽ gửi câu tiếp theo → trigger startListening lại
  };

  const handleStopPart2 = () => {
    stt.stopListening();
    exam.stopSpeakingPart2(stt.transcript || '[no response]');
  };

  const handleEndExam = () => {
    exam.endExam();
  };

  // ── Render theo state ────────────────────────────────────────────
  return (
    <div className="speaking-exam">
      {/* PART 1 & PART 3 — Hỏi đáp */}
      {(exam.examState === 'PART1_QUESTIONING' ||
        exam.examState === 'PART3_QUESTIONING') && (
        <div className="qa-section">
          <h2>Part {exam.currentQuestion?.partNumber}</h2>
          <p className="question-text">
            {exam.currentQuestion?.isFollowUp ? '↳ ' : ''}
            {exam.currentQuestion?.text}
          </p>

          {exam.isAudioPlaying && <p className="status">🔊 Đang phát audio...</p>}

          {!exam.isAudioPlaying && stt.isListening && (
            <>
              <p className="status">🎙️ Đang nghe...</p>
              <p className="transcript-live">{stt.transcript}</p>
              <button onClick={handleSubmitAnswer}>Gửi câu trả lời</button>
            </>
          )}
        </div>
      )}

      {/* PART 2 — Chuẩn bị */}
      {exam.examState === 'PART2_PREPARATION' && exam.cueCard && (
        <div className="cue-card-section">
          <h2>Part 2 — Cue Card</h2>
          <div className="cue-card">{exam.cueCard.topic}</div>
          <p>Thời gian chuẩn bị: {prepTimer}s</p>
        </div>
      )}

      {/* PART 2 — Đang nói */}
      {exam.examState === 'PART2_SPEAKING' && (
        <div className="speaking-section">
          <h2>Part 2 — Đang nói</h2>
          <p>Thời gian còn lại: {speakTimer}s</p>
          <p className="transcript-live">{stt.transcript}</p>
          <button onClick={handleStopPart2}>Dừng nói</button>
        </div>
      )}

      {/* EVALUATING */}
      {exam.examState === 'EVALUATING' && (
        <div className="evaluating">
          <h2>⏳ Đang chấm điểm...</h2>
        </div>
      )}

      {/* KẾT QUẢ */}
      {exam.examState === 'COMPLETED' && exam.evaluation && (
        <div className="result">
          <h2>📊 Kết quả</h2>
          <div className="band-score">{exam.evaluation.final_band}</div>
          <table>
            <tbody>
              <tr><td>Fluency & Coherence</td><td>{exam.evaluation.fluency}</td></tr>
              <tr><td>Lexical Resource</td><td>{exam.evaluation.vocabulary}</td></tr>
              <tr><td>Grammar</td><td>{exam.evaluation.grammar}</td></tr>
              <tr><td>Pronunciation</td><td>{exam.evaluation.pronunciation}</td></tr>
            </tbody>
          </table>
          <h3>Feedback</h3>
          <p>{exam.evaluation.feedback.summary}</p>
        </div>
      )}

      {/* ERROR */}
      {exam.examState === 'ERROR' && (
        <div className="error">
          <h2>❌ Lỗi</h2>
          <p>{exam.error}</p>
        </div>
      )}

      {/* TRANSITION */}
      {(exam.examState === 'TRANSITIONING_TO_PART2' ||
        exam.examState === 'TRANSITIONING_TO_PART3') && (
        <div className="transition">
          <p>⏳ Đang chuyển part...</p>
        </div>
      )}
    </div>
  );
}
```

---

## 4. WebSocket Message Protocol

### Client → Server

| type | payload | Khi nào gửi |
|------|---------|-------------|
| `start_exam` | `{ testId: number }` | Ngay sau khi WS connected |
| `transcript` | `{ partNumber, questionId, text }` | Sau khi user nói xong 1 câu (Part 1 & 3) |
| `start_speaking_part2` | — | Hết 60s chuẩn bị Part 2 |
| `stop_speaking_part2` | `{ text: string }` | Hết 120s nói Part 2 hoặc user bấm dừng |
| `end_exam` | — | Sau khi server gửi `evaluating` |

### Server → Client

| type | payload | Ý nghĩa |
|------|---------|---------|
| `question` | `{ partNumber, questionId, text, isFollowUp }` | Câu hỏi tiếp theo |
| `audio_chunk` | `{ data: string }` | Base64 MP3 chunk từ ElevenLabs TTS |
| `audio_end` | — | Hết audio → client phát MP3 |
| `show_cue_card` | `{ duration, questionId, topic }` | Hiện cue card Part 2, bắt đầu đếm 60s |
| `evaluating` | — | Server đang chấm điểm |
| `evaluation` | `{ final_band, fluency, vocabulary, grammar, pronunciation, feedback, transcript }` | Kết quả chấm |
| `error` | `{ message }` | Lỗi xảy ra |

---

## 5. Flow từ góc nhìn Frontend

```
┌─ IDLE ──────────────────────────────────────────────────────────────────┐
│  User bấm "Bắt đầu thi" → connect WS → send start_exam               │
└────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─ PART 1 (lặp) ─────────────────────────────────────────────────────────┐
│  1. Nhận { type: "question" } → hiện câu hỏi                          │
│  2. Nhận audio_chunk × N + audio_end → phát audio TTS                  │
│  3. Audio xong → bật mic (AssemblyAI STT)                              │
│  4. User nói xong → bấm "Gửi" → send transcript                       │
│  5. Lặp (follow-up hoặc MAIN tiếp)                                    │
└────────────────────────────────────────────────────────────────────────┘
          │ (server hết câu Part 1)
          ▼
┌─ PART 2 PREP ──────────────────────────────────────────────────────────┐
│  1. Nhận { type: "show_cue_card" } → hiện cue card                    │
│  2. Đếm ngược 60s (client tự đếm)                                     │
│  3. Hết 60s → send start_speaking_part2                                │
└────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─ PART 2 SPEAKING ──────────────────────────────────────────────────────┐
│  1. Bật mic, đếm ngược 120s                                            │
│  2. Hết 120s hoặc user bấm dừng → send stop_speaking_part2 + text      │
└────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─ PART 3 (giống Part 1) ───────────────────────────────────────────────┐
│  Lặp: nhận question → phát audio → bật mic → user nói → send          │
└────────────────────────────────────────────────────────────────────────┘
          │ (server hết câu Part 3)
          ▼
┌─ EVALUATING ───────────────────────────────────────────────────────────┐
│  1. Nhận { type: "evaluating" } → hiện loading                        │
│  2. Send end_exam                                                      │
│  3. Nhận { type: "evaluation" } → hiện kết quả                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Lưu ý quan trọng

### Thứ tự xử lý audio
```
Server gửi question JSON  →  Client hiện text câu hỏi (instant)
Server gửi audio_chunk ×N →  Client buffer
Server gửi audio_end      →  Client gộp chunks → phát MP3
Audio phát xong            →  Client bật mic (bắt đầu ghi âm + STT)
```

> **Quan trọng**: KHÔNG bật mic khi audio đang phát — sẽ ghi lại tiếng examiner!

### Environment Variables (`.env`)
```env
VITE_WS_URL=ws://localhost:8080
VITE_ASSEMBLYAI_KEY=your_assemblyai_api_key
```

### Dependencies
```bash
npm install
# Không cần thêm package nào — dùng native WebSocket + Web Audio API
# AssemblyAI STT cũng qua WebSocket, không cần SDK
```

### Xử lý mất kết nối
- Nếu WS bị đứt giữa chừng, hiện thông báo lỗi + nút "Kết nối lại"
- Backend lưu `SpeakingSession` trong DB nên có thể khôi phục (nếu implement thêm reconnect logic)

### CORS
Backend đã set `setAllowedOrigins("*")` trong `WebSocketConfig`. Production nên đổi thành domain cụ thể.
