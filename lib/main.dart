import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'voice_assistant_page.dart';

void main() {
  runApp(const TypeAssistApp());
}

class TypeAssistApp extends StatelessWidget {
  const TypeAssistApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TypeAssist',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF3B6FED),
        useMaterial3: true,
        brightness: Brightness.light,
      ),
      darkTheme: ThemeData(
        colorSchemeSeed: const Color(0xFF3B6FED),
        useMaterial3: true,
        brightness: Brightness.dark,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  static const _channel = MethodChannel('typeassist/accessibility');

  bool _serviceEnabled = false;
  bool _checking = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshStatus();
    }
  }

  Future<void> _refreshStatus() async {
    setState(() => _checking = true);
    bool enabled = false;
    try {
      enabled = await _channel.invokeMethod('isAccessibilityServiceEnabled');
    } on PlatformException {
      enabled = false;
    }
    if (!mounted) return;
    setState(() {
      _serviceEnabled = enabled;
      _checking = false;
    });
  }

  Future<void> _openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException {
      // ignore
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('TypeAssist')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Row(
                  children: [
                    Icon(
                      _serviceEnabled ? Icons.check_circle : Icons.error_outline,
                      color: _serviceEnabled ? Colors.green : Colors.orange,
                      size: 36,
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Text(
                        _checking
                            ? 'Checking status...'
                            : _serviceEnabled
                                ? 'TypeAssist is active'
                                : 'TypeAssist is not enabled yet',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'TypeAssist runs quietly in the background and watches for typing '
              'mistakes in any app - WhatsApp, Gmail, LinkedIn, or anywhere else '
              'you type. When it spots something, a small suggestion card pops up '
              'so you can fix it, or rewrite the whole message to sound more '
              'natural, with one tap.',
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: _openAccessibilitySettings,
              icon: const Icon(Icons.settings_accessibility),
              label: Text(_serviceEnabled
                  ? 'Open Accessibility Settings'
                  : 'Enable TypeAssist'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _refreshStatus,
              icon: const Icon(Icons.refresh),
              label: const Text('Refresh status'),
            ),
            const SizedBox(height: 24),
            FilledButton.tonalIcon(
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const VoiceAssistantPage()),
                );
              },
              icon: const Icon(Icons.mic),
              label: const Text('Voice Assistant (Tamil / English)'),
            ),
            const SizedBox(height: 32),
            Text(
              'How it works',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            const _StepTile(
              number: '1',
              text: 'Tap "Enable TypeAssist" above and turn the service on in '
                  'Android Settings.',
            ),
            const _StepTile(
              number: '2',
              text: 'Go to any app and start typing a message.',
            ),
            const _StepTile(
              number: '3',
              text: 'A suggestion card appears above the keyboard when a '
                  'mistake is found. Tap Apply to fix it.',
            ),
            const _StepTile(
              number: '4',
              text: 'Tap the Rewrite (sparkle) button to get a more natural, '
                  'human-sounding version of your message.',
            ),
          ],
        ),
      ),
    );
  }
}

class _StepTile extends StatelessWidget {
  final String number;
  final String text;

  const _StepTile({required this.number, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(radius: 12, child: Text(number, style: const TextStyle(fontSize: 12))),
          const SizedBox(width: 12),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}
