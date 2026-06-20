(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var accent3 = style.getPropertyValue('--accent3').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bg = style.getPropertyValue('--bg').trim();

  // --- Radar Chart: Competitiveness Comparison ---
  var chartRadar = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });

  var indicators = [
    { name: '报文转换', max: 10 },
    { name: '可视化CRUD', max: 10 },
    { name: '接口编排', max: 10 },
    { name: 'API网关', max: 10 },
    { name: '监控审计', max: 10 },
    { name: '协作生态', max: 10 },
    { name: '部署便捷', max: 10 },
    { name: '性价比', max: 10 }
  ];

  chartRadar.setOption({
    backgroundColor: 'transparent',
    legend: {
      data: ['PowerGateway', 'MuleSoft', 'Juggle', 'YesApi Pro', 'Eolink'],
      bottom: 10,
      textStyle: { color: muted, fontSize: 12 },
      itemWidth: 16,
      itemHeight: 10
    },
    radar: {
      indicator: indicators,
      shape: 'polygon',
      splitNumber: 5,
      axisName: {
        color: muted,
        fontSize: 12
      },
      splitLine: {
        lineStyle: { color: rule }
      },
      splitArea: {
        show: true,
        areaStyle: {
          color: [bg2, 'transparent']
        }
      },
      axisLine: {
        lineStyle: { color: rule }
      }
    },
    series: [{
      type: 'radar',
      animation: false,
      data: [
        {
          value: [9, 9, 3, 2, 8, 3, 7, 8],
          name: 'PowerGateway',
          lineStyle: { color: accent, width: 2 },
          areaStyle: { color: accent + '30' },
          itemStyle: { color: accent },
          symbol: 'circle',
          symbolSize: 5
        },
        {
          value: [10, 5, 9, 6, 9, 8, 4, 2],
          name: 'MuleSoft',
          lineStyle: { color: accent2, width: 2 },
          areaStyle: { color: accent2 + '20' },
          itemStyle: { color: accent2 },
          symbol: 'circle',
          symbolSize: 5
        },
        {
          value: [4, 4, 9, 2, 5, 7, 8, 10],
          name: 'Juggle',
          lineStyle: { color: accent3, width: 2 },
          areaStyle: { color: accent3 + '20' },
          itemStyle: { color: accent3 },
          symbol: 'circle',
          symbolSize: 5
        },
        {
          value: [3, 7, 2, 3, 5, 5, 7, 7],
          name: 'YesApi Pro',
          lineStyle: { color: '#22c55e', width: 2 },
          areaStyle: { color: '#22c55e20' },
          itemStyle: { color: '#22c55e' },
          symbol: 'circle',
          symbolSize: 5
        },
        {
          value: [3, 2, 3, 8, 7, 8, 6, 6],
          name: 'Eolink',
          lineStyle: { color: '#f472b6', width: 2 },
          areaStyle: { color: '#f472b620' },
          itemStyle: { color: '#f472b6' },
          symbol: 'circle',
          symbolSize: 5
        }
      ]
    }]
  });

  window.addEventListener('resize', function() { chartRadar.resize(); });
})();