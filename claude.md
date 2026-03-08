Project Overview

This project implements an AI-powered IELTS Speaking evaluation backend.

The backend receives real-time audio streams from the client, converts them into text using Speech-to-Text, evaluates the response using LLM, and stores the results.

The architecture is designed to be modular, streaming-capable, and scalable.

System Architecture

Pipeline flow:

Client
↓
WebSocket
↓
SpeakingSocketHandler
↓
SessionManager
↓
AudioStreamService
↓
SpeechToTextService
↓
ConversationEngine
↓
RuleEngine
↓
AiEvaluationRepository

Explanation:

Client streams microphone audio

Backend receives audio chunks via WebSocket

Audio chunks are buffered

When a sentence/segment is detected → sent to STT

STT returns transcript

Transcript goes to LLM evaluation

Evaluation result stored in database

Directory Responsibilities
ws/

Handles real-time communication

SpeakingSocketHandler

Responsibilities:

Handle websocket connection

Receive audio chunks

Forward audio to AudioStreamService

Send partial transcript / evaluation results back to client

Important rule:

This class must NOT contain business logic.

It should only:

receive → forward → respond
SessionManager

Manages active speaking sessions.

Responsibilities:

Create speaking session

Store session state

Store audio buffer

Track conversation progress

Session contains:

sessionId
userId
currentQuestion
audioBuffer
transcripts
evaluationState
service/

This is the core AI pipeline.

AudioStreamService

Purpose:

Process incoming audio stream.

Responsibilities:

Receive audio chunks from websocket

Append to session buffer

Detect speech boundaries

Send completed audio segments to STT

Pseudo flow:

onAudioChunk(sessionId, audioChunk):

    buffer.append(audioChunk)

    if speechSegmentDetected(buffer):

        segment = buffer.extract()

        transcript = SpeechToTextService.transcribe(segment)

        ConversationEngine.handleTranscript(sessionId, transcript)
SpeechToTextService

Responsible for speech recognition.

Current STT provider:

NVIDIA Whisper Large V3

API:

https://build.nvidia.com/openai/whisper-large-v3

Function:

String transcribe(byte[] audio)

Input:

PCM / WAV audio

Output:

text transcript

Example:

Audio: "I think technology is important"

Output:
"I think technology is important"
ConversationEngine

Central orchestrator of AI logic.

Responsibilities:

Receive transcript

Determine which task is active

Call evaluation engine

Generate feedback

Flow:

handleTranscript():

context = SessionManager.getContext()

evaluation = RuleEngine.evaluate(context, transcript)

saveEvaluation(evaluation)

return feedback
RuleEngine

Implements IELTS speaking scoring logic.

Criteria:

Fluency
Lexical Resource
Grammar
Pronunciation (optional)

This engine:

Builds prompts

Sends them to LLM

Parses structured output

Expected output format:

{
fluency: 6.5,
vocabulary: 6.0,
grammar: 6.0,
feedback: "Try using more complex sentence structures"
}
PromptLoader

Loads prompt templates.

Example:

prompts/
speaking_eval.txt
writing_task1.txt
writing_task2.txt

PromptLoader loads them at runtime.

Repository Layer

Handles persistence.

AiEvaluationRepository

Stores evaluation results.

Example entity:

AiEvaluation

id
sessionId
transcript
fluencyScore
grammarScore
vocabularyScore
feedback
createdAt
SpeakingSession Entity

Represents a speaking test session.

Fields:

id
userId
startTime
endTime
status
WebSocket Message Format

Incoming audio message:

{
type: "audio",
sessionId: "...",
chunk: base64_audio
}

Transcript response:

{
type: "transcript",
text: "I think technology is important"
}

Evaluation response:

{
type: "evaluation",
fluency: 6.5,
grammar: 6.0,
vocabulary: 6.0,
feedback: "Try to speak more naturally"
}
Implementation Order (IMPORTANT)

Claude should implement features in this order.

Step 1

Implement

SessionManager
SpeakingSession

Goal:

Manage session state.

Step 2

Implement

SpeakingSocketHandler

Goal:

Receive audio chunks from client.

Step 3

Implement

AudioStreamService

Goal:

Buffer audio and detect segments.

Step 4

Implement

SpeechToTextService

Call NVIDIA Whisper API.

Step 5

Implement

ConversationEngine

Process transcript.

Step 6

Implement

RuleEngine
PromptLoader

Perform AI evaluation.

Step 7

Implement

AiEvaluationRepository

Store results.

Important Coding Rules

Claude must follow these rules:

1

Services must be stateless.

All session state must be stored in

SessionManager
2

WebSocket layer must not call LLM directly.

Pipeline must be:

WebSocket
→ AudioStreamService
→ STT
→ ConversationEngine
3

Evaluation logic must be isolated inside

RuleEngine
4

Prompt templates must be stored in

resources/prompts/
5

Never mix infrastructure logic with AI logic.

Future Extensions

Possible improvements:

Real-time STT streaming

Replace batch STT with streaming STT.

Voice Activity Detection (VAD)

Improve speech boundary detection.

Multi-turn conversation evaluation

Track improvement across turns.