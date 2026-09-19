import React from 'react';
import { JarvisParticleOrb } from './JarvisParticleOrb';
import { LiveSessionState } from '../../services/liveSession';

interface JarvisArcOrbProps {
  state: LiveSessionState;
  isMuted: boolean;
  onTogglePower: () => void;
}

export const JarvisArcOrb: React.FC<JarvisArcOrbProps> = (props) => {
  return <JarvisParticleOrb {...props} />;
};
