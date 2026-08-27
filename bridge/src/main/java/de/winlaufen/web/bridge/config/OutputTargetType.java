package de.winlaufen.web.bridge.config;

/**
 * Target product type. This is not an exclusive selection: a bridge may run several targets at
 * once, including several of the same type. The type only drives defaults and transport policy.
 */
public enum OutputTargetType { LOCAL, SELFHOST, RICHTER_PROJECTS }
