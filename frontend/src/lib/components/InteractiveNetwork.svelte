<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import { onMount, onDestroy } from "svelte";

    let {
        nodeCount = 70, // base for ~1200x600; scales with area
        lineDistance = 130, // px
        repulsionRadius = 90, // px
        speed = 0.3, // px per frame at 60fps
        color = "255, 255, 255", // rgb
        opacity = 0.45, // base opacity for points/lines
    } = $props();
    const mouse = { x: 0, y: 0, active: false };

    let canvas;

    let ctx;
    let dpr = 1;
    let running = false;
    let rafId;
    let points = [];
    let width = 0;
    let height = 0;
    let effectiveCount = 0;

    let mql;
    onMount(() => {
        resize();
        window.addEventListener("resize", resize);
        // Track mouse globally so canvas doesn't block interactions
        const onMove = e => {
            mouse.x = e.clientX - canvas.getBoundingClientRect().left;
            mouse.y = e.clientY - canvas.getBoundingClientRect().top;
            mouse.active = true;
        };
        const onLeave = () => (mouse.active = false);
        window.addEventListener("mousemove", onMove, { passive: true });
        window.addEventListener("mouseleave", onLeave, { passive: true });

        // Respect reduced-motion
        mql = window.matchMedia("(prefers-reduced-motion: reduce)");
        if (!mql.matches) start();
        const onChange = () => (mql.matches ? stop() : start());
        mql.addEventListener?.("change", onChange);

        return () => {
            window.removeEventListener("resize", resize);
            window.removeEventListener("mousemove", onMove);
            window.removeEventListener("mouseleave", onLeave);
            mql?.removeEventListener?.("change", onChange);
        };
    });

    onDestroy(() => stop());

    function rand(min, max) {
        return Math.random() * (max - min) + min;
    }

    function resetPoints() {
        // Scale node count by area relative to a 1200x700 reference
        const area = width * height;
        const ref = 1200 * 700;
        effectiveCount = Math.max(
            20,
            Math.round(nodeCount * Math.sqrt(area / ref)),
        );
        points = Array.from({ length: effectiveCount }, () => ({
            x: rand(0, width),
            y: rand(0, height),
            vx: rand(-1, 1) * speed,
            vy: rand(-1, 1) * speed,
        }));
    }

    function resize() {
        const rect = canvas.parentElement.getBoundingClientRect();
        width = Math.max(1, Math.floor(rect.width));
        height = Math.max(1, Math.floor(rect.height));
        dpr = Math.min(2, window.devicePixelRatio || 1);
        canvas.width = Math.floor(width * dpr);
        canvas.height = Math.floor(height * dpr);
        canvas.style.width = width + "px";
        canvas.style.height = height + "px";
        ctx = canvas.getContext("2d");
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        resetPoints();
    }

    function step() {
        if (!running) return;
        ctx.clearRect(0, 0, width, height);

        // Integrate motion and repel from mouse
        for (let p of points) {
            if (mouse.active) {
                const dx = p.x - mouse.x;
                const dy = p.y - mouse.y;
                const dist = Math.hypot(dx, dy);
                if (dist < repulsionRadius && dist > 0.001) {
                    const f = (repulsionRadius - dist) / repulsionRadius; // 0..1
                    // Normalize and push away; a touch stronger than velocity
                    p.vx += (dx / dist) * f * 0.6;
                    p.vy += (dy / dist) * f * 0.6;
                }
            }

            // Slight damping to avoid runaway speeds
            p.vx *= 0.98;
            p.vy *= 0.98;

            p.x += p.vx;
            p.y += p.vy;

            // Soft wrap around edges
            if (p.x < -20) p.x = width + 20;
            if (p.x > width + 20) p.x = -20;
            if (p.y < -20) p.y = height + 20;
            if (p.y > height + 20) p.y = -20;
        }

        // Draw connections
        for (let i = 0; i < points.length; i++) {
            const p1 = points[i];
            // nodes
            ctx.beginPath();
            ctx.arc(p1.x, p1.y, 1.6, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(${color}, ${opacity})`;
            ctx.fill();

            for (let j = i + 1; j < points.length; j++) {
                const p2 = points[j];
                const dx = p1.x - p2.x;
                const dy = p1.y - p2.y;
                const dist = Math.hypot(dx, dy);
                if (dist < lineDistance) {
                    const a = (1 - dist / lineDistance) * opacity;
                    ctx.strokeStyle = `rgba(${color}, ${Math.min(0.7, Math.max(0, a))})`;
                    ctx.lineWidth = 1;
                    ctx.beginPath();
                    ctx.moveTo(p1.x, p1.y);
                    ctx.lineTo(p2.x, p2.y);
                    ctx.stroke();
                }
            }
        }

        rafId = requestAnimationFrame(step);
    }

    function start() {
        if (running) return;
        running = true;
        rafId = requestAnimationFrame(step);
    }
    function stop() {
        running = false;
        if (rafId) cancelAnimationFrame(rafId);
    }
</script>

<canvas bind:this={canvas} aria-hidden="true"></canvas>

<style>
    canvas {
        width: 100%;
        height: 100%;
        display: block;
        pointer-events: none;
        mix-blend-mode: normal;
    }
</style>
