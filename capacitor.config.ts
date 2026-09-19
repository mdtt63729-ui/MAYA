export interface CapacitorConfig {
  appId: string;
  appName: string;
  webDir: string;
  server?: {
    androidScheme?: string;
    cleartext?: boolean;
    url?: string;
  };
  android?: {
    buildOptions?: {
      keystorePath?: string;
      releaseType?: string;
    };
    backgroundColor?: string;
  };
  plugins?: Record<string, Record<string, unknown>>;
}

const config: CapacitorConfig = {
  appId: 'com.mj.assistant',
  appName: 'MJ AI Assistant',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    cleartext: true,
  },
  android: {
    buildOptions: {
      keystorePath: undefined,
      releaseType: 'APK',
    },
    backgroundColor: '#080a0f',
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1000,
      backgroundColor: '#080a0f',
      androidSplashResourceName: 'splash',
      showSpinner: false,
    },
  },
};

export default config;
