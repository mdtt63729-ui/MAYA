/**
 * Indian Multilingual Voice & Natural Pronunciation Service
 * Supports Hindi, Bengali, Indian English, Tamil, Telugu, Marathi,
 * Gujarati, Kannada, Malayalam, Punjabi, Odia, Assamese, and Urdu.
 */

import { IndianLanguageCode, LanguageInfo } from '../types';

export const INDIAN_LANGUAGES: LanguageInfo[] = [
  {
    code: 'en-IN',
    name: 'Indian English',
    nativeName: 'English (India)',
    bilingualExample: 'Open YouTube and search Android news',
    sampleGreeting: "Hi! I'm MJ. I'm here, how can I help you?",
    speechLocale: 'en-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'bn',
    name: 'Bengali',
    nativeName: 'বাংলা',
    bilingualExample: 'MJ, ইউটিউব খোলো আর গান চালাও',
    sampleGreeting: 'নমস্কার! আমি এমজে। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?',
    speechLocale: 'bn-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'hi',
    name: 'Hindi',
    nativeName: 'हिन्दी',
    bilingualExample: 'MJ, व्हाट्सएप खोलो और राहुल को मैसेज करो',
    sampleGreeting: 'नमस्ते! मैं एमजे हूँ। बताइए, मैं आपकी क्या सहायता कर सकता हूँ?',
    speechLocale: 'hi-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'ta',
    name: 'Tamil',
    nativeName: 'தமிழ்',
    bilingualExample: 'MJ, யூடியூப் திறந்து புதிய செய்திகளைத் தேடுங்கள்',
    sampleGreeting: 'வணக்கம்! நான் MJ. நான் தயாராக இருக்கிறேன், உங்களுக்கு என்ன உதவி வேண்டும்?',
    speechLocale: 'ta-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'te',
    name: 'Telugu',
    nativeName: 'తెలుగు',
    bilingualExample: 'MJ, యూట్యూబ్ తెరిచి తాజా వార్తలను వెతకండి',
    sampleGreeting: 'నమస్కారం! నేను MJ. మీకు ఎలా సహాయపడగలను?',
    speechLocale: 'te-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'mr',
    name: 'Marathi',
    nativeName: 'मराठी',
    bilingualExample: 'MJ, व्हॉट्सअॅप उघडा आणि संदेश पाठवा',
    sampleGreeting: 'नमस्कार! मी MJ आहे. मी कशी मदत करू शकतो?',
    speechLocale: 'mr-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'gu',
    name: 'Gujarati',
    nativeName: 'ગુજરાતી',
    bilingualExample: 'MJ, યુટ્યુબ ખોલો અને સમાચાર શોધો',
    sampleGreeting: 'નમસ્તે! હું MJ છું. હું તમારી શું મદદ કરી શકું?',
    speechLocale: 'gu-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'kn',
    name: 'Kannada',
    nativeName: 'ಕನ್ನಡ',
    bilingualExample: 'MJ, ಯೂಟ್ಯೂಬ್ ತೆರೆಯಿರಿ ಮತ್ತು ಹುಡುಕಿ',
    sampleGreeting: 'ನಮಸ್ಕಾರ! ನಾನು MJ. ನಿಮಗೆ ನಾನು ಹೇಗೆ ಸಹಾಯ ಮಾಡಲಿ?',
    speechLocale: 'kn-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'ml',
    name: 'Malayalam',
    nativeName: 'മലയാളം',
    bilingualExample: 'MJ, യൂട്യൂബ് തുറന്ന് വാർത്തകൾ തിരയുക',
    sampleGreeting: 'നമസ്കാരം! ഞാൻ MJ. ഞാൻ നിങ്ങളെ എങ്ങനെ സഹായിക്കണം?',
    speechLocale: 'ml-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'pa',
    name: 'Punjabi',
    nativeName: 'ਪੰਜਾਬੀ',
    bilingualExample: 'MJ, ਯੂਟਿਊਬ ਖੋਲ੍ਹੋ ਤੇ ਖ਼ਬਰਾਂ ਲੱਭੋ',
    sampleGreeting: 'ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ! ਮੈਂ MJ ਹਾਂ। ਦੱਸੋ ਮੈਂ ਤੁਹਾਡੀ ਕੀ ਮਦਦ ਕਰਾਂ?',
    speechLocale: 'pa-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'or',
    name: 'Odia',
    nativeName: 'ଓଡ଼ିଆ',
    bilingualExample: 'MJ, ୟୁଟ୍ୟୁବ୍ ଖୋଲ ଏବଂ ସନ୍ଧାନ କର',
    sampleGreeting: 'ନମସ୍କାର! ମୁଁ MJ। କୁହନ୍ତୁ, ମୁଁ ଆପଣଙ୍କୁ କିପରି ସାହାଯ୍ୟ କରିପାରିବି?',
    speechLocale: 'or-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'as',
    name: 'Assamese',
    nativeName: 'অসমীয়া',
    bilingualExample: 'MJ, ইউটিউব খোলা আৰু খবৰ বিচৰা',
    sampleGreeting: 'নমস্কাৰ! মই MJ। কওক, আপোনাক কেনেকৈ সহায় কৰিব পাৰোঁ?',
    speechLocale: 'as-IN',
    ttsVoiceName: 'Kore',
  },
  {
    code: 'ur',
    name: 'Urdu',
    nativeName: 'اردو',
    bilingualExample: 'MJ، یوٹیوب کھولیں اور تلاش کریں',
    sampleGreeting: 'آداب! میں MJ ہوں۔ بتائیے، میں آپ کی کیا مدد کر سکتا ہوں؟',
    speechLocale: 'ur-IN',
    ttsVoiceName: 'Kore',
  },
];

/**
 * Intelligent Language Detector (Devanagari, Bengali, Tamil, Telugu, etc. & romanized Hinglish/Banglish)
 */
export function detectIndianLanguage(input: string): IndianLanguageCode {
  const text = input.trim();
  if (!text) return 'en-IN';

  // Script Range Checks
  if (/[\u0980-\u09FF]/.test(text)) return 'bn'; // Bengali / Assamese
  if (/[\u0B80-\u0BFF]/.test(text)) return 'ta'; // Tamil
  if (/[\u0C00-\u0C7F]/.test(text)) return 'te'; // Telugu
  if (/[\u0A80-\u0AFF]/.test(text)) return 'gu'; // Gujarati
  if (/[\u0C80-\u0CFF]/.test(text)) return 'kn'; // Kannada
  if (/[\u0D00-\u0D7F]/.test(text)) return 'ml'; // Malayalam
  if (/[\u0A00-\u0A7F]/.test(text)) return 'pa'; // Punjabi
  if (/[\u0B00-\u0B7F]/.test(text)) return 'or'; // Odia
  if (/[\u0600-\u06FF]/.test(text)) return 'ur'; // Urdu / Arabic
  if (/[\u0900-\u097F]/.test(text)) {
    // Check Marathi specific words
    if (/\b(आहे|झाले|कसे|नाही|करा)\b/i.test(text)) return 'mr';
    return 'hi'; // Default Devanagari to Hindi
  }

  // Romanized keywords for code-switching
  const lower = text.toLowerCase();
  if (/\b(kholo|kholo|bhejo|kaise|karo|batao|chalao|kripya|namaste|aaj|kya|mujhe)\b/.test(lower)) {
    return 'hi';
  }
  if (/\b(kholo|koro|bolun|bhalo|kichu|dekho|ki|korbo|kemon|shuncho|tomar|amar|ekhon)\b/.test(lower)) {
    return 'bn';
  }
  if (/\b(vanakkam|thiravu|solunga|eppadi)\b/.test(lower)) {
    return 'ta';
  }

  return 'en-IN';
}

/**
 * Greeting Detector and Responder (Bug fix for silent greeting issue)
 * Whenever user says "Hi", "Hello", "Hi MJ", "Good morning", etc.,
 * this generates a natural, warm, immediate spoken response.
 */
export function getGreetingResponse(input: string, langCode?: IndianLanguageCode): string | null {
  const lower = input.trim().toLowerCase();
  const detectedLang = langCode || detectIndianLanguage(input);

  const isGreeting =
    /^(hi|hello|hey|hey mj|hi mj|hello mj|mj|good morning|good evening|good afternoon|are you there|kemon acho|kaise ho|vanakkam|namaste|nomoshkar|adab)[!?., ]*$/i.test(
      lower
    ) ||
    /^(hi|hello|hey)\s+(mj|assistant)[!?., ]*$/i.test(lower);

  if (!isGreeting) return null;

  switch (detectedLang) {
    case 'bn':
      return 'নমস্কার! আমি এমজে। আমি প্রস্তুত, বলুন কী করতে পারি?';
    case 'hi':
      return 'नमस्ते! मैं एमजे हूँ। मैं उपस्थित हूँ, बताइये मैं क्या कर सकता हूँ?';
    case 'ta':
      return 'வணக்கம்! நான் MJ. நான் தயாராக இருக்கிறேன், சொல்லுங்கள் என்ன செய்ய வேண்டும்?';
    case 'te':
      return 'నమస్కారం! నేను MJ. నేను సిద్ధంగా ఉన్నాను, చెప్పండి ఏమి చేయాలి?';
    case 'mr':
      return 'नमस्कार! मी MJ आहे. मी तयार आहे, सांगा काय करायचे आहे?';
    case 'gu':
      return 'નમસ્તે! હું MJ છું. હું તૈયાર છું, કહો શું મદદ કરું?';
    case 'kn':
      return 'ನಮಸ್ಕಾರ! ನಾನು MJ. ನಾನು ಸಿದ್ಧವಾಗಿದ್ದೇನೆ, ಹೇಳಿ ಏನು ಮಾಡಬೇಕು?';
    case 'ml':
      return 'നമസ്കാരം! ഞാൻ MJ. ഞാൻ തയ്യാറാണ്, പറയൂ എന്ത് ചെയ്യണം?';
    case 'pa':
      return 'ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ! ਮੈਂ MJ ਹਾਂ। ਮੈਂ ਤਿਆਰ ਹਾਂ, ਦੱਸੋ ਕੀ ਕਰਨਾ ਹੈ?';
    case 'or':
      return 'ନମସ୍କାର! ମୁଁ MJ। କୁହନ୍ତୁ, ଆପଣଙ୍କ ପାଇଁ କଣ କରିବି?';
    case 'as':
      return 'নমস্কাৰ! মই MJ। কওক, আপোনাৰ বাবে কি কৰিব পাৰোঁ?';
    case 'ur':
      return 'آداب! میں MJ ہوں۔ فرمائیے، میں آپ کی کیا خدمت کر سکتا ہوں؟';
    case 'en-IN':
    default:
      return "Hi. I'm here. What can I do for you?";
  }
}
