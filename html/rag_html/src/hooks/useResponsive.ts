import { useState, useEffect, useCallback } from 'react';

/**
 * 响应式设计相关的Hook
 * 提供移动端优化和屏幕适配功能
 */

interface ResponsiveConfig {
  mobileBreakpoint?: number;
  tabletBreakpoint?: number;
  desktopBreakpoint?: number;
}

interface ResponsiveState {
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  screenWidth: number;
  screenHeight: number;
  orientation: 'portrait' | 'landscape';
}

const DEFAULT_CONFIG: Required<ResponsiveConfig> = {
  mobileBreakpoint: 768,
  tabletBreakpoint: 1024,
  desktopBreakpoint: 1280,
};

export const useResponsive = (config: ResponsiveConfig = {}) => {
  const finalConfig = { ...DEFAULT_CONFIG, ...config };

  const [responsiveState, setResponsiveState] = useState<ResponsiveState>(() => {
    if (typeof window === 'undefined') {
      return {
        isMobile: false,
        isTablet: false,
        isDesktop: true,
        screenWidth: 1920,
        screenHeight: 1080,
        orientation: 'landscape',
      };
    }

    return {
      isMobile: window.innerWidth < finalConfig.mobileBreakpoint,
      isTablet: window.innerWidth >= finalConfig.mobileBreakpoint && window.innerWidth < finalConfig.desktopBreakpoint,
      isDesktop: window.innerWidth >= finalConfig.desktopBreakpoint,
      screenWidth: window.innerWidth,
      screenHeight: window.innerHeight,
      orientation: window.innerWidth > window.innerHeight ? 'landscape' : 'portrait',
    };
  });

  /**
   * 更新响应式状态
   */
  const updateResponsiveState = useCallback(() => {
    if (typeof window === 'undefined') return;

    const width = window.innerWidth;
    const height = window.innerHeight;

    setResponsiveState({
      isMobile: width < finalConfig.mobileBreakpoint,
      isTablet: width >= finalConfig.mobileBreakpoint && width < finalConfig.desktopBreakpoint,
      isDesktop: width >= finalConfig.desktopBreakpoint,
      screenWidth: width,
      screenHeight: height,
      orientation: width > height ? 'landscape' : 'portrait',
    });
  }, [finalConfig]);

  /**
   * 监听屏幕尺寸变化
   */
  useEffect(() => {
    if (typeof window === 'undefined') return;

    let resizeTimer: NodeJS.Timeout;

    const handleResize = () => {
      // 使用防抖来优化性能
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(updateResponsiveState, 150);
    };

    const handleOrientationChange = () => {
      // 延迟更新以确保获得正确的尺寸
      setTimeout(updateResponsiveState, 100);
    };

    window.addEventListener('resize', handleResize);
    window.addEventListener('orientationchange', handleOrientationChange);

    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('orientationchange', handleOrientationChange);
      clearTimeout(resizeTimer);
    };
  }, [updateResponsiveState]);

  /**
   * 移动端优化配置
   */
  const getMobileOptimizations = useCallback(() => {
    return {
      // 动画优化
      reduceAnimations: responsiveState.isMobile,

      // 触摸优化
      enableTouchGestures: responsiveState.isMobile,

      // 虚拟键盘优化
      adjustForKeyboard: responsiveState.isMobile,

      // 布局优化
      simplifiedLayout: responsiveState.isMobile,

      // 性能优化
      reducedMotion: responsiveState.isMobile,

      // 字体大小调整
      largerTouchTargets: responsiveState.isMobile,
    };
  }, [responsiveState.isMobile]);

  /**
   * 获取断点相关的CSS类名
   */
  const getResponsiveClasses = useCallback((baseClasses: string, mobileClasses?: string, tabletClasses?: string, desktopClasses?: string) => {
    let classes = baseClasses;

    if (responsiveState.isMobile && mobileClasses) {
      classes += ` ${mobileClasses}`;
    } else if (responsiveState.isTablet && tabletClasses) {
      classes += ` ${tabletClasses}`;
    } else if (responsiveState.isDesktop && desktopClasses) {
      classes += ` ${desktopClasses}`;
    }

    return classes;
  }, [responsiveState]);

  /**
   * 检测是否支持触摸
   */
  const isTouchDevice = useCallback(() => {
    if (typeof window === 'undefined') return false;

    return (
      'ontouchstart' in window ||
      navigator.maxTouchPoints > 0 ||
      // @ts-ignore - 兼容旧浏览器
      navigator.msMaxTouchPoints > 0
    );
  }, []);

  /**
   * 获取移动端特定的样式配置
   */
  const getMobileStyles = useCallback(() => {
    const isMobile = responsiveState.isMobile;
    const isLandscape = responsiveState.orientation === 'landscape';

    return {
      // 聊天容器样式
      chatContainer: {
        height: isMobile ? (isLandscape ? 'calc(100vh - 120px)' : 'calc(100vh - 200px)') : 'calc(100vh - 8rem)',
        maxHeight: isMobile ? (isLandscape ? '60vh' : '70vh') : 'none',
      },

      // 消息样式
      message: {
        maxWidth: isMobile ? '90%' : '85%',
        fontSize: isMobile ? '14px' : '16px',
        padding: isMobile ? '12px' : '16px',
      },

      // 输入框样式
      input: {
        fontSize: isMobile ? '16px' : '14px', // 16px防止iOS缩放
        padding: isMobile ? '12px' : '16px',
        minHeight: isMobile ? '48px' : '48px',
      },

      // 按钮样式
      button: {
        minHeight: isMobile ? '44px' : '36px', // 移动端最小触摸目标44px
        padding: isMobile ? '12px 20px' : '8px 16px',
      },

      // 动画配置
      animation: {
        duration: isMobile ? '0.15s' : '0.2s',
        easing: isMobile ? 'ease-out' : 'ease-in-out',
      },
    };
  }, [responsiveState]);

  /**
   * 虚拟键盘处理
   */
  const handleVirtualKeyboard = useCallback((callback: (keyboardHeight: number) => void) => {
    if (!responsiveState.isMobile || typeof window === 'undefined') return;

    let initialViewportHeight = window.visualViewport?.height || window.innerHeight;

    const handleVisualViewportChange = () => {
      const currentHeight = window.visualViewport?.height || window.innerHeight;
      const keyboardHeight = initialViewportHeight - currentHeight;

      if (keyboardHeight > 150) { // 键盘高度大于150px认为键盘已打开
        callback(keyboardHeight);
      }
    };

    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', handleVisualViewportChange);
      return () => {
        window.visualViewport?.removeEventListener('resize', handleVisualViewportChange);
      };
    }

    // 回退方案：监听window resize
    const handleResize = () => {
      const currentHeight = window.innerHeight;
      const keyboardHeight = initialViewportHeight - currentHeight;

      if (keyboardHeight > 150) {
        callback(keyboardHeight);
      }
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [responsiveState.isMobile]);

  /**
   * 性能优化：根据设备性能调整设置
   */
  const getPerformanceOptimizations = useCallback(() => {
    const isMobile = responsiveState.isMobile;
    const isLowEnd = isMobile && responsiveState.screenWidth < 375; // 低端移动设备

    return {
      // 消息虚拟化
      enableVirtualization: isMobile && responsiveState.screenWidth > 1000, // 大屏移动设备

      // 图片懒加载
      enableLazyLoading: isMobile,

      // 动画降级
      disableAnimations: isLowEnd,

      // 减少重渲染
      enableDebounce: isMobile,

      // 内存优化
      reduceCacheSize: isMobile,

      // 网络优化
      enableOfflineSupport: isMobile,
    };
  }, [responsiveState]);

  return {
    // 响应式状态
    ...responsiveState,

    // 工具函数
    getMobileOptimizations,
    getResponsiveClasses,
    getMobileStyles,
    getPerformanceOptimizations,
    isTouchDevice: isTouchDevice(),
    handleVirtualKeyboard,

    // 布局断点
    breakpoints: finalConfig,
  };
};