import { useEffect, useRef } from 'react';

/**
 * 一个自定义Hook，确保在React 18严格模式下useEffect只执行一次
 * 解决在开发环境中接口被请求两次的问题
 */
function useEffectOnce(effect: () => void | (() => void)) {
  const hasRun = useRef(false);

  useEffect(() => {
    if (!hasRun.current) {
      hasRun.current = true;
      const cleanup = effect();
      return cleanup;
    }
  }, []);
}

export default useEffectOnce;