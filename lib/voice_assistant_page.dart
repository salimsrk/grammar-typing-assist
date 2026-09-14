import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

/// The Gemini API key is baked in at build time via --dart-define
/// (see .github/workflows/build.yml). It is never committed to source.
const String _geminiApiKey = String.fromEnvironment('GEMINI_API_KEY');

enum _VoiceLanguage { tamil, english }

enum _Stage { idle, listening, thinking, done, error }

/// Lets the user speak a request in Tamil, English, or a mix of both
/// ("Tanglish"), then asks Gemini to turn that spoken instruction into the
/// requested content, written in clear English - e.g. speaking
/// "enaku oru simple leave mail draft pannunga" produces a ready-to-copy
/// leave email draft.
///
/// Speech-to-text uses Android's own built-in voice input (the same "Speak
/// now" dialog the Google app / keyboard mic button shows) via a small
/// native MethodChannel in MainActivity.kt - no third-party plugin, so
/// there's nothing extra to keep compatible with future Android/Gradle
/// versions.
class VoiceAssistantPage extends StatefulWidget {
  const VoiceAssistantPage({super.key});

  @override
  State<VoiceAssistantPage> createState() => _VoiceAssistantPageState();
}

class _VoiceAssistantPageState extends State<VoiceAssistantPage> {
  static const _channel = MethodChannel('typeassist/voice');

  _VoiceLanguage _language = _VoiceLanguage.tamil;
  _Stage _stage = _Stage.idle;
  String _transcript = '';
  String _result = '';
  String _errorMessage = '';

  String get _localeId => _language == _VoiceLanguage.tamil ? 'ta-IN' : 'en-IN';

  Future<void> _startListening() async {
    setState(() {
      _stage = _Stage.listening;
      _transcript = '';
      _result = '';
      _errorMessage = '';
    });

    try {
      final transcript = await _channel.invokeMethod<String>(
        'listen',
        {'localeId': _localeId},
      );

      if (transcript == null || transcript.trim().isEmpty) {
        setState(() => _stage = _Stage.idle);
        return;
      }

      setState(() => _transcript = transcript);
      await _askGemini(transcript);
    } on PlatformException catch (e) {
      setState(() {
        _stage = _Stage.error;
        _errorMessage = e.message ?? 'Could not start voice recognition.';
      });
    }
  }

  Future<void> _askGemini(String transcript) async {
    setState(() => _stage = _Stage.thinking);

    if (_geminiApiKey.isEmpty) {
      setState(() {
        _stage = _Stage.error;
        _errorMessage = 'Gemini API key is not configured for this build.';
      });
      return;
    }

    final prompt = 'The user gave this voice instruction. It may be in '
        'Tamil, English, or a mix of both ("Tanglish"):\n\n'
        '"$transcript"\n\n'
        'Understand what they are asking for and produce ONLY the '
        'requested content, written in clear, natural English. '
        'For example, if they asked for an email, message, or note, '
        'write just that text - no preamble, no explanation, no quotes '
        'around it.';

    final url = Uri.parse(
      'https://generativelanguage.googleapis.com/v1beta/models/'
      'gemini-2.0-flash:generateContent?key=$_geminiApiKey',
    );

    try {
      final response = await http
          .post(
            url,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'contents': [
                {
                  'parts': [
                    {'text': prompt}
                  ]
                }
              ]
            }),
          )
          .timeout(const Duration(seconds: 20));

      if (response.statusCode != 200) {
        setState(() {
          _stage = _Stage.error;
          _errorMessage = 'Gemini request failed (${response.statusCode}).';
        });
        return;
      }

      final data = jsonDecode(response.body);
      final text = data['candidates'][0]['content']['parts'][0]['text']
          .toString()
          .trim();

      setState(() {
        _result = text;
        _stage = _Stage.done;
      });
    } catch (e) {
      setState(() {
        _stage = _Stage.error;
        _errorMessage = 'Could not reach Gemini. Check your internet connection.';
      });
    }
  }

  Future<void> _copyResult() async {
    await Clipboard.setData(ClipboardData(text: _result));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Copied - paste it anywhere.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isBusy = _stage == _Stage.listening || _stage == _Stage.thinking;

    return Scaffold(
      appBar: AppBar(title: const Text('Voice Assistant')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            children: [
              SegmentedButton<_VoiceLanguage>(
                segments: const [
                  ButtonSegment(
                    value: _VoiceLanguage.tamil,
                    label: Text('தமிழ்'),
                    icon: Icon(Icons.translate),
                  ),
                  ButtonSegment(
                    value: _VoiceLanguage.english,
                    label: Text('English'),
                    icon: Icon(Icons.language),
                  ),
                ],
                selected: {_language},
                onSelectionChanged: isBusy
                    ? null
                    : (selection) {
                        setState(() => _language = selection.first);
                      },
              ),
              const SizedBox(height: 24),
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (_transcript.isNotEmpty) ...[
                        Text('You said', style: Theme.of(context).textTheme.labelLarge),
                        const SizedBox(height: 4),
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Text(_transcript),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                      if (_stage == _Stage.thinking)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: Column(
                            children: [
                              CircularProgressIndicator(),
                              SizedBox(height: 12),
                              Text('Thinking...'),
                            ],
                          ),
                        ),
                      if (_stage == _Stage.error)
                        Card(
                          color: Theme.of(context).colorScheme.errorContainer,
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Text(_errorMessage),
                          ),
                        ),
                      if (_stage == _Stage.done) ...[
                        Text('Result', style: Theme.of(context).textTheme.labelLarge),
                        const SizedBox(height: 4),
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(14),
                            child: SelectableText(_result),
                          ),
                        ),
                        const SizedBox(height: 12),
                        FilledButton.icon(
                          onPressed: _copyResult,
                          icon: const Icon(Icons.copy),
                          label: const Text('Copy'),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              GestureDetector(
                onTap: isBusy ? null : _startListening,
                child: CircleAvatar(
                  radius: 38,
                  backgroundColor: isBusy
                      ? Colors.grey
                      : Theme.of(context).colorScheme.primary,
                  child: isBusy
                      ? const Padding(
                          padding: EdgeInsets.all(10),
                          child: CircularProgressIndicator(color: Colors.white),
                        )
                      : const Icon(Icons.mic, color: Colors.white, size: 34),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _stage == _Stage.listening
                    ? 'Listening...'
                    : _stage == _Stage.thinking
                        ? 'Working on it...'
                        : 'Tap to speak',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
